package com.minetouhou;

import com.minetouhou.core.DataDirs;
import com.minetouhou.core.FrameCheck;
import com.minetouhou.core.GameInstall;
import com.minetouhou.core.GameKey;
import com.minetouhou.core.HelperProtocol;
import com.minetouhou.core.LootMath;
import com.minetouhou.core.MthConfig;
import com.minetouhou.core.PathGuard;
import com.minetouhou.core.TokenBucket;
import com.minetouhou.helper.JpegEncoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verificações automáticas do núcleo e do helper, só com o JDK (sem JUnit, para rodar em qualquer lugar).
 * Retorna código de saída 1 se qualquer verificação falhar.
 */
public final class SelfCheck {
	private static int passed = 0;
	private static final List<String> failures = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		lootMath();
		tokenBucket();
		protocol();
		commandParsing();
		jpegFrames();
		pathGuard();
		dataDirsAndConfig();
		gameInstall();
		System.out.println("SelfCheck: " + passed + " verificações OK, " + failures.size() + " falhas");
		for (String f : failures) System.out.println("  FALHOU: " + f);
		if (!failures.isEmpty()) System.exit(1);
	}

	private static void check(boolean ok, String name) {
		if (ok) passed++;
		else failures.add(name);
	}

	private static void lootMath() {
		check(Math.abs(LootMath.chanceFromOneIn(5000) - 0.0002f) < 1e-9, "chance PC = 1/5000");
		check(Math.abs(LootMath.chanceFromOneIn(9000) - (1f / 9000f)) < 1e-9, "chance CD = 1/9000");
		check(LootMath.chanceFromOneIn(0) == 0f, "N=0 desativa");
		check(Math.abs(LootMath.percent(5000) - 0.02) < 1e-12, "5000 -> 0,02%");
		check(Math.abs(LootMath.percent(9000) - 0.011111) < 1e-5, "9000 -> ~0,0111%");
		List<String> p = new MthConfig().lootTablePrefixes;
		check(LootMath.isEligibleTable("chests/simple_dungeon", p), "masmorra elegível");
		check(LootMath.isEligibleTable("chests/abandoned_mineshaft", p), "mina elegível");
		check(LootMath.isEligibleTable("chests/desert_pyramid", p), "templo elegível");
		check(LootMath.isEligibleTable("chests/village/village_weaponsmith", p), "vila elegível");
		check(!LootMath.isEligibleTable("entities/zombie", p), "entidades não elegíveis");
		check(!LootMath.isEligibleTable("blocks/stone", p), "blocos não elegíveis");
		check(!LootMath.isEligibleTable("chests/end_city_treasure", p), "end city fora da lista");
	}

	private static void tokenBucket() {
		TokenBucket b = new TokenBucket(3, 1, 0);
		check(b.tryConsume(1, 0) && b.tryConsume(1, 0) && b.tryConsume(1, 0), "balde: 3 fichas iniciais");
		check(!b.tryConsume(1, 0), "balde: vazio recusa");
		check(b.tryConsume(1, 1000), "balde: reabastece 1/s");
		check(!b.tryConsume(1, 1100), "balde: ainda sem ficha");
		check(b.tryConsume(1, 5000) && b.tryConsume(1, 5000) && b.tryConsume(1, 5000) && !b.tryConsume(1, 5000),
			"balde: respeita a capacidade");
	}

	private static void protocol() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		HelperProtocol.writeStatus(bos, "RUNNING", "Capturando o jogo.");
		byte[] fake = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};
		HelperProtocol.writeFrame(bos, 256, 192, fake);
		ByteArrayInputStream in = new ByteArrayInputStream(bos.toByteArray());
		HelperProtocol.Message m1 = HelperProtocol.read(in);
		HelperProtocol.Message m2 = HelperProtocol.read(in);
		check(m1 != null && m1.type() == HelperProtocol.TYPE_STATUS && m1.state().equals("RUNNING")
			&& m1.message().equals("Capturando o jogo."), "protocolo: status ida e volta");
		check(m2 != null && m2.type() == HelperProtocol.TYPE_FRAME && m2.width() == 256 && m2.height() == 192
			&& java.util.Arrays.equals(m2.jpeg(), fake), "protocolo: frame ida e volta");
		check(HelperProtocol.read(in) == null, "protocolo: fim de fluxo limpo");

		boolean rejected = false;
		try {
			HelperProtocol.read(new ByteArrayInputStream(new byte[] {1, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
		} catch (IOException e) {
			rejected = true;
		}
		check(rejected, "protocolo: tamanho absurdo recusado");
		rejected = false;
		try {
			HelperProtocol.read(new ByteArrayInputStream(new byte[] {9, 0, 0, 0, 0}));
		} catch (IOException e) {
			rejected = true;
		}
		check(rejected, "protocolo: tipo desconhecido recusado");
	}

	private static void commandParsing() {
		check(HelperProtocol.parseCommand("PING").kind() == HelperProtocol.Command.Kind.PING, "cmd: PING");
		check(HelperProtocol.parseCommand("QUIT").kind() == HelperProtocol.Command.Kind.QUIT, "cmd: QUIT");
		HelperProtocol.Command c = HelperProtocol.parseCommand("KEY DOWN Z");
		check(c != null && c.kind() == HelperProtocol.Command.Kind.KEY_DOWN && c.key() == GameKey.Z, "cmd: KEY DOWN Z");
		c = HelperProtocol.parseCommand("key up shift");
		check(c != null && c.kind() == HelperProtocol.Command.Kind.KEY_UP && c.key() == GameKey.SHIFT, "cmd: minúsculas");
		check(HelperProtocol.parseCommand("KEY DOWN F4") == null, "cmd: tecla fora da lista recusada");
		check(HelperProtocol.parseCommand("KEY DOWN Z; rm -rf /") == null, "cmd: injeção recusada");
		check(HelperProtocol.parseCommand("EXEC ls") == null, "cmd: comando desconhecido recusado");
		check(HelperProtocol.parseCommand("") == null, "cmd: vazio recusado");
		check(HelperProtocol.parseCommand("KEY SIDEWAYS Z") == null, "cmd: ação inválida recusada");
		check(HelperProtocol.parseCommand("PING extra") == null, "cmd: argumentos extras recusados");
		check(HelperProtocol.keyLine(true, GameKey.LEFT).equals("KEY DOWN LEFT"), "cmd: keyLine");
	}

	private static void jpegFrames() throws IOException {
		BufferedImage img = new BufferedImage(256, 192, BufferedImage.TYPE_3BYTE_BGR);
		for (int y = 0; y < 192; y++) for (int x = 0; x < 256; x++) img.setRGB(x, y, (x * 7 + y * 13) * 0x010203);
		JpegEncoder enc = new JpegEncoder(0.7f, 0.15f, 0.85f);
		byte[] jpg = enc.encode(img, 200_000);
		check(jpg != null && FrameCheck.looksLikeJpeg(jpg), "jpeg: codificado e reconhecido");
		int[] size = jpg == null ? null : FrameCheck.jpegSize(jpg);
		check(size != null && size[0] == 256 && size[1] == 192, "jpeg: tamanho lido do cabeçalho");
		check(jpg != null && FrameCheck.isAcceptable(jpg, 256, 192, 200_000), "jpeg: quadro válido aceito");
		check(jpg != null && !FrameCheck.isAcceptable(jpg, 255, 192, 200_000), "jpeg: tamanho declarado errado recusado");
		check(jpg != null && !FrameCheck.isAcceptable(jpg, 256, 192, 100), "jpeg: acima do limite de bytes recusado");
		check(!FrameCheck.isAcceptable(new byte[] {1, 2, 3, 4, 5, 6}, 256, 192, 1000), "jpeg: lixo recusado");
		check(!FrameCheck.isAcceptable(null, 256, 192, 1000), "jpeg: null recusado");
		BufferedImage big = new BufferedImage(1024, 768, BufferedImage.TYPE_3BYTE_BGR);
		byte[] bigJpg = enc.encode(big, 900_000);
		check(bigJpg != null && !FrameCheck.isAcceptable(bigJpg, 1024, 768, 900_000), "jpeg: dimensão acima do limite recusada");
		// Com um limite de bytes bem apertado o codificador reduz a qualidade ou descarta, nunca excede.
		byte[] tight = enc.encode(img, 3_000);
		check(tight == null || tight.length <= 3_000, "jpeg: nunca excede o limite de bytes");
		enc.close();
	}

	private static void pathGuard() throws IOException {
		Path dir = Files.createTempDirectory("mth-pg");
		Path exe = dir.resolve("th06");
		Files.write(exe, new byte[] {0x7F, 'E', 'L', 'F', 0, 0, 0, 0});
		check(PathGuard.resolveExecutable(dir, "th06").equals(exe.toRealPath()), "pathguard: executável válido");
		check(throwsIO(() -> PathGuard.resolveExecutable(dir, "../th06")), "pathguard: .. recusado");
		check(throwsIO(() -> PathGuard.resolveExecutable(dir, "/bin/sh")), "pathguard: caminho absoluto recusado");
		check(throwsIO(() -> PathGuard.resolveExecutable(dir, "a\\b")), "pathguard: barra invertida recusada");
		check(throwsIO(() -> PathGuard.resolveExecutable(dir, "")), "pathguard: vazio recusado");
		check(throwsIO(() -> PathGuard.resolveExecutable(dir, "nao-existe")), "pathguard: inexistente recusado");
		Path outside = Files.createTempFile("mth-out", "");
		try {
			Files.createSymbolicLink(dir.resolve("link"), outside);
			check(throwsIO(() -> PathGuard.resolveExecutable(dir, "link")), "pathguard: symlink para fora recusado");
		} catch (UnsupportedOperationException | IOException e) {
			// sistema sem symlinks: ignora
		}
		Files.createDirectory(dir.resolve("subdir"));
		check(throwsIO(() -> PathGuard.resolveExecutable(dir, "subdir")), "pathguard: diretório recusado");
	}

	private interface IOAction { void run() throws IOException; }

	private static boolean throwsIO(IOAction a) {
		try {
			a.run();
			return false;
		} catch (IOException e) {
			return true;
		}
	}

	private static void dataDirsAndConfig() throws IOException {
		Path root = Files.createTempDirectory("mth-data").resolve("minetouhou");
		DataDirs.ensure(root);
		check(Files.isDirectory(DataDirs.game(root)) && Files.isDirectory(DataDirs.config(root))
			&& Files.isDirectory(DataDirs.logs(root)), "dirs: game/config/logs criados");
		check(Files.isRegularFile(root.resolve("LEIA-ME.txt")), "dirs: LEIA-ME.txt criado");
		Files.writeString(root.resolve("LEIA-ME.txt"), "editado");
		DataDirs.ensure(root);
		check(Files.readString(root.resolve("LEIA-ME.txt")).equals("editado"), "dirs: não sobrescreve o LEIA-ME existente");
		check(DataDirs.readmeText().contains("9f76483c46256804792399296619c1274363c31cd8f1775fafb55106fb852245"),
			"dirs: LEIA-ME cita o hash");

		Path cfg = DataDirs.config(root).resolve("minetouhou.properties");
		new MthConfig().writeDefaultIfMissing(cfg);
		MthConfig c = MthConfig.load(cfg);
		check(c.pcLootOneIn == 5000 && c.cdLootOneIn == 9000, "config: padrões 5000/9000 lidos de volta");
		Files.writeString(cfg, "pcLootOneIn=10\ncdLootOneIn=abc\nfps=999\nmaxViewers=-5\nlootTablePrefixes=chests/a, chests/b\n");
		c = MthConfig.load(cfg);
		check(c.pcLootOneIn == 10, "config: valor customizado");
		check(c.cdLootOneIn == 9000, "config: número inválido cai no padrão");
		check(c.fps == 30, "config: fps limitado a 30");
		check(c.maxViewers == 0, "config: maxViewers negativo vira 0");
		Files.writeString(cfg, "maxFrameBytes=999999\n");
		check(MthConfig.load(cfg).maxFrameBytes == MthConfig.WIRE_MAX_FRAME_BYTES, "config: maxFrameBytes limitado ao teto da rede");
		check(c.lootTablePrefixes.equals(List.of("chests/a", "chests/b")), "config: prefixos em lista");
	}

	private static void gameInstall() throws IOException {
		Path dir = Files.createTempDirectory("mth-game");
		GameInstall.Result r = GameInstall.verify(dir, null);
		check(!r.valid(), "install: pasta vazia é inválida");
		check(r.errors().stream().anyMatch(e -> e.contains("Nenhum executável")), "install: avisa que falta executável");

		byte[] elf = {0x7F, 'E', 'L', 'F', 0, 0, 0, 0};
		Files.write(dir.resolve("th06"), elf);
		r = GameInstall.verify(dir, null);
		check(!r.valid() && r.errors().stream().anyMatch(e -> e.contains(".DAT")), "install: sem .DAT é inválida");

		byte[] dat = new byte[200_000];
		dat[0] = 'P'; dat[1] = 'B'; dat[2] = 'G'; dat[3] = '3';
		for (String n : GameInstall.DAT_FILES) Files.write(dir.resolve(n), dat);
		r = GameInstall.verify(dir, "th06");
		check(!r.valid() && r.errors().stream().anyMatch(e -> e.contains("msgothic.ttc")), "install: exige msgothic.ttc no port");
		Files.write(dir.resolve("msgothic.ttc"), new byte[10]);
		r = GameInstall.verify(dir, "th06");
		check(r.valid() && r.kind() == GameInstall.ExeKind.ELF, "install: instalação completa do port é válida");
		check(r.version().contains("port portable"), "install: não afirma versão que não pode provar");

		Files.write(dir.resolve(GameInstall.DAT_FILES[2]), new byte[50]);
		r = GameInstall.verify(dir, "th06");
		check(!r.valid(), "install: .DAT truncado é recusado");
		Files.write(dir.resolve(GameInstall.DAT_FILES[2]), dat);

		Files.write(dir.resolve("th06"), new byte[] {1, 2, 3, 4, 5, 6});
		r = GameInstall.verify(dir, "th06");
		check(!r.valid() && r.errors().stream().anyMatch(e -> e.contains("não reconhecido")), "install: executável de formato desconhecido recusado");

		Files.write(dir.resolve("th06"), new byte[] {'M', 'Z', 0, 0, 0, 0});
		r = GameInstall.verify(dir, "th06");
		check(r.kind() == GameInstall.ExeKind.PE && r.warnings().stream().anyMatch(w -> w.contains("SHA-256 diferente")),
			"install: .exe de hash diferente gera aviso");
	}
}
