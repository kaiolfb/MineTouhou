package com.minetouhou;

import com.minetouhou.block.PcBlock;
import com.minetouhou.block.PcBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registro do bloco PC, do item CD e da entidade de bloco. */
public final class MthRegistry {
	public static final String MOD_ID = "minetouhou";

	public static final Block PC_BLOCK = Registry.register(Registries.BLOCK, id("pc"),
		new PcBlock(AbstractBlock.Settings.create()
			.strength(1.5f, 6.0f)
			.sounds(BlockSoundGroup.METAL)
			.nonOpaque()
			.pistonBehavior(PistonBehavior.NORMAL)));

	public static final Item PC_ITEM = Registry.register(Registries.ITEM, id("pc"),
		new BlockItem(PC_BLOCK, new Item.Settings()));

	/** O CD: item plano e fino (modelo item/generated), um por pilha de 16. */
	public static final Item CD_ITEM = Registry.register(Registries.ITEM, id("cd"),
		new Item(new Item.Settings().maxCount(16)));

	public static final BlockEntityType<PcBlockEntity> PC_BLOCK_ENTITY = Registry.register(
		Registries.BLOCK_ENTITY_TYPE, id("pc"),
		BlockEntityType.Builder.create(PcBlockEntity::new, PC_BLOCK).build());

	private MthRegistry() {}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	/** Força o carregamento da classe (e portanto dos registros acima). */
	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(PC_ITEM);
			entries.add(CD_ITEM);
		});
	}
}
