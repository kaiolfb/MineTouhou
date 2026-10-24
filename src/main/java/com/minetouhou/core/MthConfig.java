package com.minetouhou.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Configuração em {@code minetouhou/config/minetouhou.properties} (só JDK, sem Gson).
 * Valores inválidos caem no padrão e são limitados a faixas seguras.
 */
public final class MthConfig {
	/** Teto de bytes por quadro JPEG na rede (pacotes cliente->servidor devem caber em 32 KiB). */
	public static final int WIRE_MAX_FRAME_BYTES = 30_000;

	public int pcLootOneIn = 5000;
	public int cdLootOneIn = 9000;
	/** Prefixos de loot tables elegíveis (todos dentro de chests/). */
	public List<String> lootTablePrefixes = List.of(
		"chests/simple_dungeon", "chests/abandoned_mineshaft", "chests/desert_pyramid",
		"chests/jungle_temple", "chests/village/", "chests/stronghold_", "chests/woodland_mansion",
		"chests/ruined_portal", "chests/shipwreck_", "chests/underwater_ruin_");

	/** Pasta do jogo; vazio = {@code <dados>/game}. */
	public String gameDir = "";
	/** Nome do executável dentro da pasta do jogo; vazio = detectar. */
	public String exeName = "";
	/** Comando java usado para iniciar o helper. */
	public String helperJava = "java";

	public int fps = 12;
	public int width = 256;
	public int maxFrameBytes = 30_000;

	public int maxViewers = 8;
	public int viewDistance = 24;
	/** Teto de bytes/s retransmitidos pelo servidor por sessão. */
	public int maxBytesPerSecond = 600_000;

	/** Código GLFW da tecla que sai do modo de jogo (padrão: F10 = 291). */
	public int exitKey = 291;

	public static MthConfig load(Path file) {
		MthConfig c = new MthConfig();
		Properties p = new Properties();
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException e) {
				// usa os padrões
			}
		}
		c.pcLootOneIn = clamp(num(p, "pcLootOneIn", c.pcLootOneIn), 0, 100_000_000);
		c.cdLootOneIn = clamp(num(p, "cdLootOneIn", c.cdLootOneIn), 0, 100_000_000);
		String prefixes = p.getProperty("lootTablePrefixes");
		if (prefixes != null && !prefixes.isBlank()) {
			c.lootTablePrefixes = Arrays.stream(prefixes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		}
		c.gameDir = p.getProperty("gameDir", c.gameDir).trim();
		c.exeName = p.getProperty("exeName", c.exeName).trim();
		c.helperJava = p.getProperty("helperJava", c.helperJava).trim();
		if (c.helperJava.isEmpty()) c.helperJava = "java";
		c.fps = clamp(num(p, "fps", c.fps), 1, 30);
		c.width = clamp(num(p, "width", c.width), 64, 640);
		c.maxFrameBytes = clamp(num(p, "maxFrameBytes", c.maxFrameBytes), 2_000, WIRE_MAX_FRAME_BYTES);
		c.maxViewers = clamp(num(p, "maxViewers", c.maxViewers), 0, 64);
		c.viewDistance = clamp(num(p, "viewDistance", c.viewDistance), 4, 128);
		c.maxBytesPerSecond = clamp(num(p, "maxBytesPerSecond", c.maxBytesPerSecond), 20_000, 20_000_000);
		c.exitKey = clamp(num(p, "exitKey", c.exitKey), 32, 348);
		return c;
	}

	/** Grava o arquivo com explicações, se ainda não existir. */
	public void writeDefaultIfMissing(Path file) throws IOException {
		if (Files.exists(file)) return;
		Files.createDirectories(file.getParent());
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			w.write("# MineTouhou - configuração\n");
			w.write("# Chances de loot: \"1 em N\" por tentativa de loot elegível (N=0 desativa o item).\n");
			w.write("# Cada vez que um baú elegível gera seu conteúdo, o pool do MineTouhou é rolado UMA vez\n");
			w.write("# com probabilidade 1/N. Ex.: 5000 -> 0,02% ; 9000 -> ~0,0111%.\n");
			w.write("pcLootOneIn=" + pcLootOneIn + "\n");
			w.write("cdLootOneIn=" + cdLootOneIn + "\n");
			w.write("# Prefixos (separados por vírgula) das loot tables elegíveis; todos dentro de chests/.\n");
			w.write("lootTablePrefixes=" + String.join(",", lootTablePrefixes) + "\n\n");
			w.write("# Pasta do jogo (vazio = <pasta de dados>/game) e nome do executável (vazio = detectar).\n");
			w.write("gameDir=\n");
			w.write("exeName=\n");
			w.write("# Comando java usado para iniciar o helper (padrão: o do PATH).\n");
			w.write("helperJava=java\n\n");
			w.write("# Captura/transmissão (o servidor também limita o que retransmite).\n");
			w.write("fps=" + fps + "\n");
			w.write("width=" + width + "\n");
			w.write("maxFrameBytes=" + maxFrameBytes + "\n");
			w.write("maxViewers=" + maxViewers + "\n");
			w.write("viewDistance=" + viewDistance + "\n");
			w.write("maxBytesPerSecond=" + maxBytesPerSecond + "\n\n");
			w.write("# Tecla de saída do modo de jogo (código GLFW; 291 = F10).\n");
			w.write("exitKey=" + exitKey + "\n");
		}
	}

	private static int num(Properties p, String key, int def) {
		try {
			String v = p.getProperty(key);
			return v == null ? def : Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
