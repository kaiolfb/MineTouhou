package com.minetouhou.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cria {@code minetouhou/{game,config,logs}} e o {@code LEIA-ME.txt}. Nunca exige permissão de administrador. */
public final class DataDirs {
	private DataDirs() {}

	/** Ordem: -Dminetouhou.dir, variável MINETOUHOU_DIR, {@code ~/minetouhou}. */
	public static Path resolveRoot() {
		String prop = System.getProperty("minetouhou.dir");
		if (prop != null && !prop.isBlank()) return Path.of(prop).toAbsolutePath().normalize();
		String env = System.getenv("MINETOUHOU_DIR");
		if (env != null && !env.isBlank()) return Path.of(env).toAbsolutePath().normalize();
		return Path.of(System.getProperty("user.home", "."), "minetouhou").toAbsolutePath().normalize();
	}

	public static Path game(Path root) { return root.resolve("game"); }
	public static Path config(Path root) { return root.resolve("config"); }
	public static Path logs(Path root) { return root.resolve("logs"); }

	/** Cria o que faltar. Retorna a raiz. Lança IOException se não houver permissão de escrita. */
	public static Path ensure(Path root) throws IOException {
		Files.createDirectories(game(root));
		Files.createDirectories(config(root));
		Files.createDirectories(logs(root));
		Path readme = root.resolve("LEIA-ME.txt");
		if (!Files.exists(readme)) {
			Files.writeString(readme, readmeText(), StandardCharsets.UTF_8);
		}
		return root;
	}

	public static String readmeText() {
		return """
			MINETOUHOU (MTH) - LEIA-ME
			==========================

			Esta pasta é criada automaticamente pelo mod MineTouhou. Ela NÃO contém o Touhou 6:
			você precisa fornecer a sua própria cópia legítima do jogo.

			1) ONDE COLOCAR OS ARQUIVOS DO TOUHOU 6
			   Coloque tudo na pasta  game/  (ou aponte outra pasta em config/minetouhou.properties, chave gameDir).

			   Opção A - port portable (recomendado em Linux; é o único modo que o helper atual testa):
			     - O binário compilado do port (th06) -- veja docs/INTEGRACAO-TOUHOU6.md para compilar.
			     - Os 6 arquivos de dados do SEU jogo original: 紅魔郷CM.DAT, 紅魔郷ED.DAT, 紅魔郷IN.DAT,
			       紅魔郷MD.DAT, 紅魔郷ST.DAT, 紅魔郷TL.DAT
			     - Uma fonte chamada msgothic.ttc (MS Gothic original ou compatível, como Kochi Gothic).
			   Opção B - executável original do Windows (東方紅魔郷.exe v1.02h) via Wine (não testado):
			     - O .exe e os mesmos 6 arquivos .DAT.

			2) VERSÃO ESPERADA
			   Touhou 6: Embodiment of Scarlet Devil, versão 1.02h.
			   O 東方紅魔郷.exe original v1.02h tem SHA-256
			   9f76483c46256804792399296619c1274363c31cd8f1775fafb55106fb852245.
			   O port portable é compilado do código da decompilação 1.02h; a versão de um binário
			   compilado por você não pode ser provada pelo mod (ele avisa isso).

			3) COMO VERIFICAR
			   No Minecraft, abra o PC e clique em "Verificar arquivos". O resultado aparece na tela.

			4) COMO INICIAR
			   Coloque o CD MineTouhou no PC (botão "Inserir CD"), verifique os arquivos e clique em
			   "Iniciar Touhou 6". Para jogar, clique em "Jogar" (captura o teclado). A tecla de saída
			   padrão é F10 (config: exitKey).

			5) CAMINHO DO EXECUTÁVEL
			   config/minetouhou.properties -> exeName=th06  (nome do arquivo dentro de game/; sem caminhos).

			6) SOLUÇÃO DE ERROS
			   - Veja logs/helper.log (o helper escreve tudo lá).
			   - "Ferramenta ausente": instale xvfb, ffmpeg e xdotool (Debian/Ubuntu: sudo apt install xvfb ffmpeg xdotool).
			   - "Falta msgothic.ttc": copie uma fonte com esse nome para game/.
			   - Tela preta: veja o log; o jogo pode ter falhado ao abrir (arquivos .DAT ausentes/corrompidos).
			   - Nomes japoneses não lidos: inicie o Minecraft com LC_ALL=C.UTF-8.

			7) SISTEMAS SUPORTADOS
			   Helper de captura: Linux com X11 (Xvfb). Windows e macOS: NÃO suportados nesta versão
			   (o mod funciona, mas não consegue iniciar/capturar o jogo; mostra erro claro).
			   Servidor dedicado: não executa o jogo; apenas retransmite quadros (qualquer sistema com Java 21).
			""";
	}
}
