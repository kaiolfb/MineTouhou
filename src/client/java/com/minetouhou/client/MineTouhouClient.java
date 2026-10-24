package com.minetouhou.client;

import com.minetouhou.MthRegistry;
import com.minetouhou.block.PcBlock;
import com.minetouhou.net.MthPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class MineTouhouClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PcBlock.clientScreenOpener = (world, pos) -> MinecraftClient.getInstance().setScreen(new PcScreen(pos));
		BlockEntityRendererFactories.register(MthRegistry.PC_BLOCK_ENTITY, PcBlockEntityRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(MthPayloads.FrameOut.ID,
			(payload, ctx) -> MonitorTextures.submit(payload.pos(), payload.width(), payload.height(), payload.jpeg()));
		ClientPlayNetworking.registerGlobalReceiver(MthPayloads.Session.ID, (payload, ctx) -> {
			if (payload.start()) ClientSessions.onSessionStart(payload.pos());
			else ClientSessions.onSessionStop(payload.pos());
		});

		ClientTickEvents.END_CLIENT_TICK.register(ClientSessions::clientTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSessions.onDisconnect());
	}
}
