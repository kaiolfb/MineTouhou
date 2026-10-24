package com.minetouhou.helper;

import com.minetouhou.core.GameInstall;
import com.minetouhou.core.HelperProtocol;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processo auxiliar do MineTouhou (roda na máquina do jogador responsável pela sessão).
 *
 * <p>Em Linux ele cria um servidor X virtual (Xvfb) com 640x480, executa o jogo NESSE display,
 * captura o display com o ffmpeg (x11grab), codifica cada quadro em JPEG e o envia ao mod pelo
 * stdout. As teclas chegam pelo stdin e são injetadas no display virtual com o xdotool - por isso
 * não disputam o foco com a janela do Minecraft.
 *
 * <p>Esta classe só usa o JDK. O mod a inicia com {@code java -cp <jar do mod> com.minetouhou.helper.HelperMain}.
 * O helper NUNCA executa nada que venha pelo stdin além de {@code xdotool key} de teclas da lista fechada
 * {@link com.minetouhou.core.GameKey}.
 */
public final class HelperMain {
	private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);

	private static OutputStream out;
	private static Path logFile;
	private static Process xvfb;
	private static Process game;
	private static Process ffmpeg;
	private static String display;

	private HelperMain() {}

	public static void main(String[] argv) {
		// stdout é o canal binário do protocolo: qualquer println perdido vai para stderr.
		out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), 1 << 16);
		System.setOut(new PrintStream(System.err, true));

		Map<String, String> args = parseArgs(argv);
		if (args.containsKey("log")) logFile = Path.of(args.get("log"));
		int code;
		try {
			code = run(args);
		} catch (Throwable t) {
			log("Erro fatal: " + t);
			status("ERROR", "Falha interna do helper: " + t.getClass().getSimpleName() + ": " + t.getMessage());
			code = 1;
		}
		shutdown();
		System.exit(code);
	}

	private static int run(Map<String, String> args) throws Exception {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (!os.contains("linux")) {
			status("ERROR", "Esta versão do helper só suporta Linux (X11). Sistema atual: " + os
				+ ". Veja docs/INTEGRACAO-TOUHOU6.md.");
			return 2;
		}

		Path gameDir = Path.of(require(args, "game-dir"));
		String exeName = args.getOrDefault("exe", "");
		int fps = clamp(intArg(args, "fps", 12), 1, 30);
		int width = clamp(intArg(args, "width", 256), 64, 640);
		int height = clamp(intArg(args, "height", width * 3 / 4), 48, 480);
		int maxBytes = clamp(intArg(args, "max-frame-bytes", 30_000), 2_000, 900_000);

		status("STARTING", "Verificando arquivos do jogo...");
		GameInstall.Result check = GameInstall.verify(gameDir, exeName.isBlank() ? null : exeName);
		if (!check.valid()) {
			status("ERROR", "Instalação inválida: " + String.join("; ", check.errors()));
			return 3;
		}
		Path exe = check.executable();

		for (String tool : List.of("Xvfb", "ffmpeg", "xdotool")) {
			if (findInPath(tool) == null) {
				status("ERROR", "Ferramenta ausente no PATH: " + tool
					+ ". Em Debian/Ubuntu: sudo apt install xvfb ffmpeg xdotool.");
				return 4;
			}
		}
		boolean needsWine = check.kind() == GameInstall.ExeKind.PE;
		if (needsWine && findInPath("wine") == null) {
			status("ERROR", "O executável é do Windows e o wine não está no PATH.");
			return 4;
		}

		display = pickDisplay();
		if (display == null) {
			status("ERROR", "Nenhum display X livre entre :90 e :140.");
			return 5;
		}
		status("STARTING", "Iniciando display virtual " + display + "...");
		xvfb = new ProcessBuilder("Xvfb", display, "-screen", "0", "640x480x24", "-nolisten", "tcp", "-noreset")
			.redirectErrorStream(true)
			.redirectOutput(logRedirect())
			.start();
		if (!waitForSocket(display, 5000)) {
			status("ERROR", "O Xvfb não iniciou a tempo (veja o log do helper).");
			return 5;
		}

		status("STARTING", "Iniciando o jogo...");
		List<String> gameCmd = new ArrayList<>();
		if (needsWine) gameCmd.add("wine");
		gameCmd.add(exe.toString());
		ProcessBuilder gpb = new ProcessBuilder(gameCmd)
			.directory(gameDir.toRealPath().toFile())
			.redirectErrorStream(true)
			.redirectOutput(logRedirect())
			.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
		gpb.environment().put("DISPLAY", display);
		gpb.environment().put("SDL_VIDEODRIVER", "x11");
		gpb.environment().remove("WAYLAND_DISPLAY");
		gpb.environment().remove("XAUTHORITY");
		game = gpb.start();

		ffmpeg = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
			"-f", "x11grab", "-draw_mouse", "0", "-framerate", Integer.toString(fps),
			"-video_size", "640x480", "-i", display,
			"-vf", "scale=" + width + ":" + height,
			"-pix_fmt", "bgr24", "-f", "rawvideo", "-")
			.redirectError(logRedirect())
			.start();

		Thread capture = new Thread(() -> captureLoop(width, height, fps, maxBytes), "mth-capture");
		capture.setDaemon(true);
		capture.start();

		Thread watcher = new Thread(() -> {
			try {
				int rc = game.waitFor();
				if (!SHUTTING_DOWN.get()) {
					status("EXITED", "O jogo terminou (código " + rc + ").");
					shutdown();
					System.exit(0);
				}
			} catch (InterruptedException ignored) {
				// encerrando
			}
		}, "mth-game-watch");
		watcher.setDaemon(true);
		watcher.start();

		Runtime.getRuntime().addShutdownHook(new Thread(HelperMain::shutdown));
		commandLoop();
		return 0;
	}

	// ------------------------------------------------------------------ captura

	private static void captureLoop(int width, int height, int fps, int maxBytes) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		int frameBytes = width * height * 3;
		DataInputStream in = new DataInputStream(ffmpeg.getInputStream());
		JpegEncoder enc;
		try {
			enc = new JpegEncoder(0.7f, 0.15f, 0.85f);
		} catch (IOException e) {
			status("ERROR", e.getMessage());
			return;
		}
		boolean sawContent = false;
		boolean warnedBlack = false;
		boolean warnedDrop = false;
		long blackFrames = 0;
		try {
			while (!SHUTTING_DOWN.get()) {
				in.readFully(pixels, 0, frameBytes);
				boolean black = isBlack(pixels);
				if (!black && !sawContent) {
					sawContent = true;
					status("RUNNING", "Capturando o jogo.");
				}
				if (black && !sawContent) {
					blackFrames++;
					if (!warnedBlack && blackFrames > fps * 10L) {
						warnedBlack = true;
						status("WARN", "Tela preta há 10 s: o jogo pode não ter iniciado. Veja logs/helper.log.");
					}
				}
				byte[] jpeg = enc.encode(img, maxBytes);
				if (jpeg == null) {
					if (!warnedDrop) {
						warnedDrop = true;
						status("WARN", "Quadros acima de " + maxBytes + " bytes mesmo na qualidade mínima; descartando.");
					}
					continue;
				}
				HelperProtocol.writeFrame(out, width, height, jpeg);
			}
		} catch (EOFException e) {
			if (!SHUTTING_DOWN.get()) {
				status("ERROR", "A captura (ffmpeg) terminou inesperadamente. Veja logs/helper.log.");
				shutdown();
				System.exit(6);
			}
		} catch (IOException e) {
			// stdout quebrado = o mod fechou; encerra em silêncio
			if (!SHUTTING_DOWN.get()) {
				log("Saída fechada: " + e);
				shutdown();
				System.exit(0);
			}
		} finally {
			enc.close();
		}
	}

	/** Amostra 1 a cada 64 pixels; "preto" = todos os canais amostrados &lt;= 2. */
	static boolean isBlack(byte[] bgr) {
		for (int i = 0; i + 2 < bgr.length; i += 64 * 3) {
			if ((bgr[i] & 0xFF) > 2 || (bgr[i + 1] & 0xFF) > 2 || (bgr[i + 2] & 0xFF) > 2) return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ comandos

	private static void commandLoop() throws IOException {
		ExecutorService keys = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "mth-keys");
			t.setDaemon(true);
			return t;
		});
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String line;
		while (!SHUTTING_DOWN.get() && (line = in.readLine()) != null) {
			if (line.length() > 64) continue; // nenhum comando válido é tão longo
			HelperProtocol.Command cmd = HelperProtocol.parseCommand(line);
			if (cmd == null) {
				log("Comando recusado.");
				continue;
			}
			switch (cmd.kind()) {
				case QUIT -> {
					keys.shutdownNow();
					return;
				}
				case PING -> status("PONG", "");
				case KEY_DOWN -> keys.execute(() -> xdotool("keydown", cmd.key().x11Keysym));
				case KEY_UP -> keys.execute(() -> xdotool("keyup", cmd.key().x11Keysym));
			}
		}
		keys.shutdownNow();
		// EOF no stdin: o mod morreu ou fechou o canal -> encerra tudo (run() chama shutdown()).
	}

	private static void xdotool(String action, String keysym) {
		try {
			ProcessBuilder pb = new ProcessBuilder("xdotool", action, keysym) // sem --clearmodifiers: ele re-pressiona o Shift após o keyup
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			pb.environment().put("DISPLAY", display);
			Process p = pb.start();
			if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly();
		} catch (IOException | InterruptedException e) {
			log("xdotool falhou: " + e);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private static synchronized void shutdown() {
		if (!SHUTTING_DOWN.compareAndSet(false, true)) return;
		stop(game, 2000);
		stop(ffmpeg, 0);
		stop(xvfb, 0);
		try {
			out.flush();
		} catch (IOException ignored) {
			// canal já fechado
		}
	}

	private static void stop(Process p, long graceMs) {
		if (p == null) return;
		try {
			if (graceMs > 0 && p.isAlive()) {
				p.destroy();
				p.waitFor(graceMs, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
		if (p.isAlive()) p.destroyForcibly();
	}

	private static synchronized void status(String state, String message) {
		try {
			HelperProtocol.writeStatus(out, state, message);
		} catch (IOException e) {
			log("Não foi possível enviar status: " + e);
		}
		log(state + " " + message);
	}

	private static synchronized void log(String msg) {
		if (logFile == null) return;
		try {
			Files.writeString(logFile, Instant.now() + " " + msg + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// log é best-effort
		}
	}

	private static ProcessBuilder.Redirect logRedirect() {
		if (logFile == null) return ProcessBuilder.Redirect.DISCARD;
		return ProcessBuilder.Redirect.appendTo(logFile.toFile());
	}

	private static String pickDisplay() {
		for (int n = 90; n <= 140; n++) {
			if (!Files.exists(Path.of("/tmp/.X11-unix/X" + n)) && !Files.exists(Path.of("/tmp/.X" + n + "-lock"))) {
				return ":" + n;
			}
		}
		return null;
	}

	private static boolean waitForSocket(String disp, long timeoutMs) throws InterruptedException {
		Path sock = Path.of("/tmp/.X11-unix/X" + disp.substring(1));
		long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			if (Files.exists(sock)) return true;
			if (xvfb != null && !xvfb.isAlive()) return false;
			Thread.sleep(50);
		}
		return Files.exists(sock);
	}

	static Path findInPath(String tool) {
		String path = System.getenv("PATH");
		if (path == null) return null;
		for (String dir : path.split(File.pathSeparator)) {
			if (dir.isBlank()) continue;
			Path p = Path.of(dir, tool);
			if (Files.isRegularFile(p) && Files.isExecutable(p)) return p;
		}
		return null;
	}

	private static Map<String, String> parseArgs(String[] argv) {
		Map<String, String> m = new java.util.HashMap<>();
		for (int i = 0; i + 1 < argv.length; i += 2) {
			if (argv[i].startsWith("--")) m.put(argv[i].substring(2), argv[i + 1]);
		}
		return m;
	}

	private static String require(Map<String, String> a, String k) {
		String v = a.get(k);
		if (v == null || v.isBlank()) throw new IllegalArgumentException("argumento obrigatório ausente: --" + k);
		return v;
	}

	private static int intArg(Map<String, String> a, String k, int def) {
		try {
			return a.containsKey(k) ? Integer.parseInt(a.get(k).trim()) : def;
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
