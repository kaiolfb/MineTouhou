package com.minetouhou.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Validação de caminhos antes de executar qualquer programa. */
public final class PathGuard {
	private PathGuard() {}

	/**
	 * Resolve o executável dentro da pasta do jogo. Recusa nomes com separadores, "..", caracteres
	 * de controle, links simbólicos que escapem da pasta e arquivos que não sejam regulares.
	 */
	public static Path resolveExecutable(Path gameDir, String exeName) throws IOException {
		if (exeName == null || exeName.isBlank()) {
			throw new IOException("Nome do executável vazio.");
		}
		if (exeName.contains("/") || exeName.contains("\\") || exeName.contains("..")) {
			throw new IOException("O executável deve ser um nome de arquivo dentro da pasta do jogo (sem caminhos).");
		}
		for (int i = 0; i < exeName.length(); i++) {
			if (Character.isISOControl(exeName.charAt(i))) {
				throw new IOException("Nome do executável contém caracteres de controle.");
			}
		}
		if (!Files.isDirectory(gameDir)) {
			throw new IOException("Pasta do jogo não existe: " + gameDir);
		}
		Path root = gameDir.toRealPath();
		Path exe = root.resolve(exeName);
		if (!Files.exists(exe)) {
			throw new IOException("Executável não encontrado: " + exeName);
		}
		Path real = exe.toRealPath();
		if (!real.startsWith(root)) {
			throw new IOException("O executável resolve para fora da pasta do jogo.");
		}
		if (!Files.isRegularFile(real)) {
			throw new IOException("O executável não é um arquivo regular.");
		}
		return real;
	}
}
