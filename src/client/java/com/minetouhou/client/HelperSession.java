package com.minetouhou.client;

import com.minetouhou.MineTouhou;
import com.minetouhou.core.FrameCheck;
import com.minetouhou.core.GameKey;
import com.minetouhou.core.HelperProtocol;
import com.minetouhou.core.MthConfig;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Processo auxiliar (helper) em execução na máquina do jogador responsável. */
public final class HelperSession {
	public interface Listener {
		void onStatus(String state, String message);
		void onFrame(int width, int height, byte[] jpeg);
		/** Chamado uma única vez quando o helper termina (por qualquer motivo, exceto stop() pedido). */
		void onExit(int exitCode);
	}

	private final Process process;
	private final OutputStream stdin;
	private final AtomicBoolean stopped = new AtomicBoolean(false);

	private HelperSession(Process p) {
		this.process = p;
		this.stdin = p.getOutputStream();
	}

	public static HelperSession start(MthConfig cfg, Path gameDir, Path logFile, Listener listener) throws IOException {
		List<String> cmd = new ArrayList<>();
		cmd.add(cfg.helperJava);
		cmd.add("-Xmx128m");
		cmd.add("-Djava.awt.headless=true");
		cmd.add("-Dfile.encoding=UTF-8");
		cmd.add("-cp");
		cmd.add(helperClasspath());
		cmd.add("com.minetouhou.helper.HelperMain");
		cmd.add("--game-dir");
		cmd.add(gameDir.toString());
		cmd.add("--exe");
		cmd.add(cfg.exeName);
		cmd.add("--fps");
		cmd.add(Integer.toString(cfg.fps));
		cmd.add("--width");
		cmd.add(Integer.toString(cfg.width));
		cmd.add("--max-frame-bytes");
		cmd.add(Integer.toString(cfg.maxFrameBytes));
		cmd.add("--log");
		cmd.add(logFile.toString());

		ProcessBuilder pb = new ProcessBuilder(cmd)
			.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
		pb.environment().putIfAbsent("LC_ALL", "C.UTF-8");
		HelperSession s = new HelperSession(pb.start());
		Thread reader = new Thread(() -> s.readLoop(cfg, listener), "mth-helper-reader");
		reader.setDaemon(true);
		reader.start();
		return s;
	}

	private void readLoop(MthConfig cfg, Listener l) {
		try {
			HelperProtocol.Message m;
			while ((m = HelperProtocol.read(process.getInputStream())) != null) {
				if (m.type() == HelperProtocol.TYPE_STATUS) {
					l.onStatus(m.state(), m.message());
				} else if (FrameCheck.isAcceptable(m.jpeg(), m.width(), m.height(), cfg.maxFrameBytes)) {
					l.onFrame(m.width(), m.height(), m.jpeg());
				}
			}
		} catch (IOException e) {
			MineTouhou.LOGGER.warn("Canal do helper encerrado: {}", e.toString());
		}
		int code = -1;
		try {
			if (process.waitFor(2, TimeUnit.SECONDS)) code = process.exitValue();
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
		if (!stopped.get()) l.onExit(code);
	}

	public boolean isAlive() { return process.isAlive(); }

	public void sendKey(boolean down, GameKey key) {
		sendLine(HelperProtocol.keyLine(down, key));
	}

	private synchronized void sendLine(String line) {
		try {
			stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
			stdin.flush();
		} catch (IOException ignored) {
			// helper já terminou
		}
	}

	/** Encerra o helper (e, por ele, o jogo). Seguro chamar mais de uma vez. */
	public void stop() {
		if (!stopped.compareAndSet(false, true)) return;
		sendLine("QUIT");
		Thread t = new Thread(() -> {
			try {
				if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
			} catch (InterruptedException e) {
				process.destroyForcibly();
			}
		}, "mth-helper-stop");
		t.setDaemon(true);
		t.start();
	}

	/** Caminho do JAR do mod (ou das pastas de classes no ambiente de desenvolvimento). */
	private static String helperClasspath() throws IOException {
		try {
			ModContainer mc = FabricLoader.getInstance().getModContainer("minetouhou").orElseThrow();
			List<String> parts = new ArrayList<>();
			for (Path p : mc.getOrigin().getPaths()) parts.add(p.toAbsolutePath().toString());
			if (!parts.isEmpty()) return String.join(File.pathSeparator, parts);
		} catch (RuntimeException e) {
			throw new IOException("Não foi possível localizar o JAR do mod: " + e.getMessage(), e);
		}
		throw new IOException("Não foi possível localizar o JAR do mod.");
	}
}
