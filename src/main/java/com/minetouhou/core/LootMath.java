package com.minetouhou.core;

import java.util.List;

/**
 * Cálculo das chances de loot e seleção das tabelas elegíveis.
 *
 * <p>"1 em N por tentativa" vira uma única condição {@code random_chance} com probabilidade
 * {@code 1/N} em um pool de 1 rolagem. Cada vez que um baú elegível gera seu conteúdo
 * (uma "tentativa de loot elegível") o pool é rolado exatamente uma vez.
 */
public final class LootMath {
	private LootMath() {}

	/** Probabilidade (0..1) para "1 em oneIn". Valores &lt;= 0 desativam o item (retorna 0). */
	public static float chanceFromOneIn(int oneIn) {
		if (oneIn <= 0) {
			return 0f;
		}
		return (float) (1.0 / oneIn);
	}

	/** Chance em porcentagem, para documentação e logs. */
	public static double percent(int oneIn) {
		return oneIn <= 0 ? 0.0 : 100.0 / oneIn;
	}

	/**
	 * Uma tabela é elegível se o caminho dela (ex.: {@code chests/simple_dungeon}) começa com
	 * algum dos prefixos configurados. Só caminhos dentro de {@code chests/} são aceitos.
	 */
	public static boolean isEligibleTable(String path, List<String> prefixes) {
		if (path == null || !path.startsWith("chests/")) {
			return false;
		}
		for (String prefix : prefixes) {
			if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}
}
