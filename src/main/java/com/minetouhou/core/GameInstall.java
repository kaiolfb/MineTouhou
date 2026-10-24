package com.minetouhou.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verifica uma instalação do Touhou 6 (EoSD) fornecida pelo jogador.
 *
 * <p>Não há como "provar" a versão de um executável compilado pelo próprio jogador a partir do
 * port portable; por isso a verificação reporta exatamente o que foi confirmado:
 * <ul>
 *   <li>tipo do executável (ELF / PE / Mach-O) pelos bytes mágicos;</li>
 *   <li>SHA-256 do executável original, quando for o {@code 東方紅魔郷.exe} v1.02h;</li>
 *   <li>presença, tamanho mínimo e cabeçalho "PBG3" dos seis arquivos .DAT;</li>
 *   <li>presença da fonte {@code msgothic.ttc} exigida pelo port.</li>
 * </ul>
 */
public final class GameInstall {
	/** SHA-256 do 東方紅魔郷.exe v1.02h, conforme o README do projeto de decompilação. */
	public static final String ORIGINAL_EXE_SHA256 =
		"9f76483c46256804792399296619c1274363c31cd8f1775fafb55106fb852245";

	public static final String[] DAT_FILES = {
		"紅魔郷CM.DAT", "紅魔郷ED.DAT", "紅魔郷IN.DAT",
		"紅魔郷MD.DAT", "紅魔郷ST.DAT", "紅魔郷TL.DAT"
	};

	private static final String[] EXE_CANDIDATES = {
		"th06", "th06.exe", "東方紅魔郷.exe"
	};

	private static final long MIN_DAT_BYTES = 100_000L;

	public enum ExeKind { ELF, PE, MACHO, UNKNOWN }

	public record Result(
		boolean valid,
		String gameName,
		String version,
		Path executable,
		ExeKind kind,
		List<String> errors,
		List<String> warnings
	) {
		public String summary() {
			StringBuilder sb = new StringBuilder();
			sb.append(valid ? "OK" : "INVÁLIDA").append(" - ").append(gameName).append(" - ").append(version);
			for (String e : errors) sb.append("\n  [erro] ").append(e);
			for (String w : warnings) sb.append("\n  [aviso] ").append(w);
			return sb.toString();
		}
	}

	private GameInstall() {}

	/** @param exeName nome configurado do executável, ou null/vazio para detectar automaticamente. */
	public static Result verify(Path gameDir, String exeName) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		String gameName = "Touhou 6: Embodiment of Scarlet Devil";

		if (gameDir == null || !Files.isDirectory(gameDir)) {
			errors.add("A pasta do jogo não existe: " + gameDir);
			return new Result(false, gameName, "desconhecida", null, ExeKind.UNKNOWN, errors, warnings);
		}

		Path exe = null;
		try {
			if (exeName != null && !exeName.isBlank()) {
				exe = PathGuard.resolveExecutable(gameDir, exeName);
			} else {
				for (String candidate : EXE_CANDIDATES) {
					try {
						exe = PathGuard.resolveExecutable(gameDir, candidate);
						break;
					} catch (IOException ignored) {
						// tenta o próximo candidato
					}
				}
				if (exe == null) {
					errors.add("Nenhum executável encontrado (procurado: th06, th06.exe, 東方紅魔郷.exe). "
						+ "Configure o nome do executável.");
				}
			}
		} catch (IOException e) {
			errors.add(e.getMessage());
		}

		ExeKind kind = ExeKind.UNKNOWN;
		String version = "não confirmada";
		if (exe != null) {
			try {
				kind = detectKind(exe);
				switch (kind) {
					case PE -> {
						String sha = sha256(exe);
						if (ORIGINAL_EXE_SHA256.equalsIgnoreCase(sha)) {
							version = "1.02h (executável original, SHA-256 confirmado)";
						} else {
							warnings.add("Executável do Windows com SHA-256 diferente do v1.02h original; "
								+ "pode não ser a versão 1.02h.");
						}
						if (!isWindows()) {
							warnings.add("Executável do Windows: em Linux/macOS só roda via Wine (não testado).");
						}
					}
					case ELF, MACHO ->
						version = "port portable (código da decompilação 1.02h; versão do binário não verificável)";
					default -> errors.add("Formato de executável não reconhecido (esperado ELF, PE ou Mach-O).");
				}
			} catch (IOException e) {
				errors.add("Falha ao ler o executável: " + e.getMessage());
			}
		}

		checkDataFiles(gameDir, errors, warnings);

		if (kind == ExeKind.ELF || kind == ExeKind.MACHO) {
			if (!Files.isRegularFile(gameDir.resolve("msgothic.ttc"))) {
				errors.add("Falta msgothic.ttc na pasta do jogo (o port portable exige uma fonte com esse nome; "
					+ "pode ser o MS Gothic ou uma fonte compatível, como Kochi Gothic).");
			}
		}

		return new Result(errors.isEmpty(), gameName, version, exe, kind, errors, warnings);
	}

	private static void checkDataFiles(Path gameDir, List<String> errors, List<String> warnings) {
		int missingByName = 0;
		for (String name : DAT_FILES) {
			Path p = gameDir.resolve(name);
			if (!Files.isRegularFile(p)) {
				missingByName++;
				continue;
			}
			try {
				long size = Files.size(p);
				if (size < MIN_DAT_BYTES) {
					errors.add("Arquivo de dados pequeno demais (corrompido?): " + name);
					continue;
				}
				byte[] head = new byte[4];
				try (InputStream in = Files.newInputStream(p)) {
					int n = in.readNBytes(head, 0, 4);
					if (n < 4 || !new String(head, StandardCharsets.US_ASCII).equals("PBG3")) {
						warnings.add("Cabeçalho inesperado (esperado PBG3): " + name);
					}
				}
			} catch (IOException e) {
				errors.add("Falha ao ler " + name + ": " + e.getMessage());
			}
		}
		if (missingByName == 0) {
			return;
		}
		// Se a JVM não consegue enxergar nomes japoneses (locale sem UTF-8), os arquivos podem existir
		// mesmo assim. Conta .DAT por sufixo para dar um diagnóstico melhor.
		int datCount = 0;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(gameDir)) {
			for (Path p : ds) {
				String n = p.getFileName().toString().toUpperCase(Locale.ROOT);
				if (n.endsWith(".DAT")) datCount++;
			}
		} catch (IOException ignored) {
			// já tratado abaixo
		}
		if (datCount >= DAT_FILES.length && missingByName == DAT_FILES.length) {
			warnings.add("Há " + datCount + " arquivos .DAT mas a JVM não consegue ler nomes japoneses. "
				+ "Inicie o Minecraft com LC_ALL=C.UTF-8 (Linux) para uma verificação completa.");
		} else {
			errors.add("Faltam " + missingByName + " dos 6 arquivos .DAT (紅魔郷CM/ED/IN/MD/ST/TL.DAT).");
		}
	}

	public static ExeKind detectKind(Path exe) throws IOException {
		byte[] h = new byte[4];
		try (InputStream in = Files.newInputStream(exe)) {
			if (in.readNBytes(h, 0, 4) < 4) return ExeKind.UNKNOWN;
		}
		if ((h[0] & 0xFF) == 0x7F && h[1] == 'E' && h[2] == 'L' && h[3] == 'F') return ExeKind.ELF;
		if (h[0] == 'M' && h[1] == 'Z') return ExeKind.PE;
		int m = ((h[0] & 0xFF) << 24) | ((h[1] & 0xFF) << 16) | ((h[2] & 0xFF) << 8) | (h[3] & 0xFF);
		if (m == 0xCFFAEDFE || m == 0xFEEDFACF || m == 0xCAFEBABE) return ExeKind.MACHO;
		if (h[0] == '#' && h[1] == '!') return ExeKind.ELF; // script de inicialização (shell) tratado como nativo
		return ExeKind.UNKNOWN;
	}

	static String sha256(Path p) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(p)) {
				byte[] buf = new byte[1 << 16];
				int n;
				while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
