package com.minetouhou.server;

import com.minetouhou.MineTouhou;
import com.minetouhou.block.PcBlockEntity;
import com.minetouhou.block.PcState;
import com.minetouhou.core.FrameCheck;
import com.minetouhou.net.MthPayloads;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Lado servidor do MineTouhou. NÃO executa o jogo: só guarda o CD/estado do PC, decide quem é o jogador
 * responsável pela sessão e retransmite os quadros JPEG dele para os espectadores próximos.
 *
 * <p>Tudo que chega da rede é tratado como hostil: o jogador precisa estar perto do PC, a posição precisa
 * conter um PC, os relatórios só valem se vierem do dono da sessão, os quadros são validados
 * (cabeçalho JPEG, dimensões, bytes) e limitados em taxa (quadros/s e bytes/s de saída).
 */
public final class ServerSessions {
	private static final double MAX_REACH_SQ = 8.0 * 8.0;
	private static final Set<String> REPORT_STATES = Set.of("VERIFYING", "RUNNING", "ERROR", "READY");

	private ServerSessions() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(MthPayloads.Action.ID, MthPayloads.Action.CODEC);
		PayloadTypeRegistry.playC2S().register(MthPayloads.Report.ID, MthPayloads.Report.CODEC);
		PayloadTypeRegistry.playC2S().register(MthPayloads.Frame.ID, MthPayloads.Frame.CODEC);
		PayloadTypeRegistry.playS2C().register(MthPayloads.FrameOut.ID, MthPayloads.FrameOut.CODEC);
		PayloadTypeRegistry.playS2C().register(MthPayloads.Session.ID, MthPayloads.Session.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MthPayloads.Action.ID,
			(payload, ctx) -> onAction(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(MthPayloads.Report.ID,
			(payload, ctx) -> onReport(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(MthPayloads.Frame.ID,
			(payload, ctx) -> onFrame(ctx.player(), payload));
	}

	/** O PC em {@code pos}, se o jogador está perto e o chunk está carregado; senão null. */
	private static @Nullable PcBlockEntity pcNear(ServerPlayerEntity player, BlockPos pos) {
		ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos)) return null;
		if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_REACH_SQ) return null;
		return world.getBlockEntity(pos) instanceof PcBlockEntity pc ? pc : null;
	}

	private static int nowTick(ServerPlayerEntity player) {
		return (int) (player.getServerWorld().getTime() & 0x7FFFFFFF);
	}

	private static void onAction(ServerPlayerEntity player, MthPayloads.Action a) {
		PcBlockEntity pc = pcNear(player, a.pos());
		if (pc == null) return;
		switch (a.action()) {
			case MthPayloads.Action.INSERT_CD -> pc.insertCdFrom(player);
			case MthPayloads.Action.EJECT_CD -> pc.ejectCdTo(player);
			case MthPayloads.Action.START -> {
				if (pc.tryStartSession(player, nowTick(player))) {
					ServerPlayNetworking.send(player, new MthPayloads.Session(a.pos(), true));
				}
			}
			case MthPayloads.Action.STOP -> {
				// só o jogador responsável encerra a sessão dele
				if (pc.pcState().sessionActive() && player.getUuid().equals(pc.owner())) {
					pc.endSession(null);
				}
			}
			case MthPayloads.Action.RESET -> pc.resetFromError();
			default -> { }
		}
	}

	private static void onReport(ServerPlayerEntity player, MthPayloads.Report r) {
		PcBlockEntity pc = pcNear(player, r.pos());
		if (pc == null || !pc.pcState().sessionActive() || !player.getUuid().equals(pc.owner())) return;
		if (!REPORT_STATES.contains(r.state())) return;
		pc.applyOwnerReport(r.state(), sanitize(r.message()), nowTick(player));
	}

	private static String sanitize(String s) {
		StringBuilder sb = new StringBuilder(Math.min(s.length(), 200));
		for (int i = 0; i < s.length() && sb.length() < 200; i++) {
			char c = s.charAt(i);
			sb.append(Character.isISOControl(c) ? ' ' : c);
		}
		return sb.toString();
	}

	private static void onFrame(ServerPlayerEntity player, MthPayloads.Frame f) {
		// Os quadros chegam de um jogador que pode estar a vários blocos do PC (a tela de jogo é remota),
		// mas ainda assim precisa ser o dono da sessão deste PC.
		ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(f.pos())) return;
		if (!(world.getBlockEntity(f.pos()) instanceof PcBlockEntity pc)) return;
		if (!pc.pcState().sessionActive() || !player.getUuid().equals(pc.owner())) return;
		var cfg = MineTouhou.CONFIG;
		if (!FrameCheck.isAcceptable(f.jpeg(), f.width(), f.height(), cfg.maxFrameBytes)) return;

		List<ServerPlayerEntity> viewers = world.getPlayers(p ->
			p != player
				&& p.squaredDistanceTo(f.pos().getX() + 0.5, f.pos().getY() + 0.5, f.pos().getZ() + 0.5)
					<= (double) cfg.viewDistance * cfg.viewDistance
				&& ServerPlayNetworking.canSend(p, MthPayloads.FrameOut.ID));
		viewers.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(f.pos().getX() + 0.5, f.pos().getY() + 0.5, f.pos().getZ() + 0.5)));
		if (viewers.size() > cfg.maxViewers) viewers = viewers.subList(0, cfg.maxViewers);

		pc.touchSignal(nowTick(player));
		if (!pc.allowFrame(f.jpeg().length, viewers.size(), cfg.fps + 2, cfg.maxBytesPerSecond, System.currentTimeMillis())) {
			return; // acima do limite de taxa: descarta o quadro (o próximo chega logo)
		}
		if (pc.pcState() == PcState.VERIFYING) {
			// primeiro quadro válido: o jogo está de fato produzindo imagem
			pc.applyOwnerReport("RUNNING", "Jogando.", nowTick(player));
		}
		MthPayloads.FrameOut out = new MthPayloads.FrameOut(f.pos(), f.width(), f.height(), f.jpeg());
		for (ServerPlayerEntity v : viewers) {
			ServerPlayNetworking.send(v, out);
		}
	}
}
