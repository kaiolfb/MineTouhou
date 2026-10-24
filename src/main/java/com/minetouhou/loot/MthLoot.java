package com.minetouhou.loot;

import com.minetouhou.MineTouhou;
import com.minetouhou.MthRegistry;
import com.minetouhou.core.LootMath;
import com.minetouhou.core.MthConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;

/**
 * Loot raro por tentativa de loot elegível.
 *
 * <p>Para cada loot table de baú vanilla elegível (prefixos em {@code lootTablePrefixes}) é ADICIONADO um pool
 * novo por item, com 1 rolagem e condição {@code random_chance} de 1/N. Os pools originais ficam intactos, então
 * nenhum item do baú é removido, e como cada tabela ganha exatamente um pool por item não existem rolagens duplicadas.
 * Cada vez que um baú gera seu conteúdo (primeira abertura) é uma tentativa. Blocos comuns e mobs não são tocados.
 */
public final class MthLoot {
	private MthLoot() {}

	public static void register(MthConfig cfg) {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			// Só tabelas embutidas (vanilla/mods): não mexe em tabelas de data packs de terceiros.
			if (!source.isBuiltin()) return;
			if (!"minecraft".equals(key.getValue().getNamespace())) return;
			if (!LootMath.isEligibleTable(key.getValue().getPath(), cfg.lootTablePrefixes)) return;
			addPool(tableBuilder, MthRegistry.PC_ITEM, cfg.pcLootOneIn);
			addPool(tableBuilder, MthRegistry.CD_ITEM, cfg.cdLootOneIn);
		});
		MineTouhou.LOGGER.info("Loot: PC 1 em {} ({}%), CD 1 em {} ({}%) por tentativa de loot elegível.",
			cfg.pcLootOneIn, String.format("%.4f", LootMath.percent(cfg.pcLootOneIn)),
			cfg.cdLootOneIn, String.format("%.4f", LootMath.percent(cfg.cdLootOneIn)));
	}

	private static void addPool(net.minecraft.loot.LootTable.Builder table, Item item, int oneIn) {
		float chance = LootMath.chanceFromOneIn(oneIn);
		if (chance <= 0f) return; // N=0 desativa
		table.pool(LootPool.builder()
			.rolls(ConstantLootNumberProvider.create(1))
			.conditionally(RandomChanceLootCondition.builder(chance))
			.with(ItemEntry.builder(item)));
	}
}
