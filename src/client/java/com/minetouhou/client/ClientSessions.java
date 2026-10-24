package com.minetouhou.client;

import com.minetouhou.MineTouhou;
import com.minetouhou.block.PcBlockEntity;
import com.minetouhou.block.PcState;
import com.minetouhou.core.DataDirs;
import com.minetouhou.core.GameInstall;
import com.minetouhou.core.GameKey;
import com.minetouhou.net.MthPayloads;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Estado local do jogador responsável: verificação dos arquivos, helper em execução, envio de quadros.
 * Só existe no cliente. Tudo que toca disco/processos roda fora da thread principal do Minecraft.
 */
public final class ClientSessions {
	private static volatile @Nullable HelperSession helper;
	private static volatile @Nullable BlockPos activePos;
	private static volatile long startedAtMs;
	private static volatile String lastState = "VERIFYING";
	private static volatile String lastMessage = "";
	private static volatile boolean frameSendPending;
	private static int tickCounter;

	/** Resultado da última verificação (para a tela do PC). */
	public static volatile @Nullable GameInstall.Result lastVerify;
	public static volatile boolean verifying;

	private ClientSessions() {}

	public static Path gameDir() {
		String g = MineTouhou.CONFIG.gameDir;
		return g.isBlank() ? DataDirs.game(MineTouhou.ROOT) : Path.of(g);
	}

	public static boolean isActiveFor(BlockPos pos) {
		HelperSession h = helper;
		return h != null && h.isAlive() && pos.equals(activePos);
	}

	// ------------------------------------------------------------------ verificação

	public static void verifyAsync() {
		if (verifying) return;
		verifying = true;
		Thread t = new Thread(() -> {
			try {
				String exe = MineTouhou.CONFIG.exeName;
				lastVerify = GameInstall.verify(gameDir(), exe.isBlank() ? null : exe);
			} catch (RuntimeException e) {
				MineTouhou.LOGGER.error("Falha ao verificar o jogo", e);
			} finally {
				verifying = false;
			}
		}, "mth-verify");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------ sessão

	/** O servidor aceitou: este jogador é o responsável. Verifica e inicia o helper em segundo plano. */
	public static void onSessionStart(BlockPos pos) {
		stopLocal();
		activePos = pos;
		startedAtMs = System.currentTimeMillis();
		lastState = "VERIFYING";
		lastMessage = "Verificando arquivos...";
		Thread t = new Thread(() -> launch(pos), "mth-launch");
		t.setDaemon(true);
		t.start();
	}

	private static void launch(BlockPos pos) {
		try {
			Path root = MineTouhou.ROOT;
			DataDirs.ensure(root);
			String exe = MineTouhou.CONFIG.exeName;
			GameInstall.Result r = GameInstall.verify(gameDir(), exe.isBlank() ? null : exe);
			lastVerify = r;
			if (!r.valid()) {
				fail(pos, "Instalação inválida: " + String.join("; ", r.errors()));
				return;
			}
			report(pos, "VERIFYING", "Iniciando o jogo...");
			HelperSession h = HelperSession.start(MineTouhou.CONFIG, gameDir(),
				DataDirs.logs(root).resolve("helper.log"), new HelperSession.Listener() {
					@Override
					public void onStatus(String state, String message) {
						switch (state) {
							case "ERROR" -> fail(pos, message);
							case "EXITED" -> { stopLocal(); report(pos, "READY", message); }
							case "RUNNING", "WARN", "STARTING" -> {
								lastState = state.equals("RUNNING") ? "RUNNING" : "VERIFYING";
								lastMessage = message;
								report(pos, lastState, message);
							}
							default -> { }
						}
					}

					@Override
					public void onFrame(int w, int h2, byte[] jpeg) {
						MonitorTextures.submit(pos, w, h2, jpeg); // o monitor local mostra o quadro direto
						if (!frameSendPending) {
							frameSendPending = true;
							MinecraftClient.getInstance().execute(() -> {
								frameSendPending = false;
								if (MinecraftClient.getInstance().getNetworkHandler() != null) {
									ClientPlayNetworking.send(new MthPayloads.Frame(pos, w, h2, jpeg));
								}
							});
						}
					}

					@Override
					public void onExit(int code) {
						helper = null;
						if (pos.equals(activePos)) {
							activePos = null;
							report(pos, code == 0 ? "READY" : "ERROR",
								code == 0 ? "O jogo terminou." : "O helper terminou com erro (código " + code + "). Veja logs/helper.log.");
						}
					}
				});
			if (!pos.equals(activePos)) { // o jogador mandou parar enquanto iniciava
				h.stop();
				return;
			}
			helper = h;
		} catch (Exception e) {
			MineTouhou.LOGGER.error("Falha ao iniciar o helper", e);
			fail(pos, "Falha ao iniciar o helper: " + e.getMessage());
		}
	}

	private static void fail(BlockPos pos, String message) {
		stopLocal();
		report(pos, "ERROR", message);
	}

	private static void report(BlockPos pos, String state, String message) {
		lastState = state;
		lastMessage = message == null ? "" : message;
		MinecraftClient.getInstance().execute(() -> {
			if (MinecraftClient.getInstance().getNetworkHandler() != null) {
				ClientPlayNetworking.send(new MthPayloads.Report(pos, state, lastMessage));
			}
		});
	}

	/** Para o helper local e esquece a sessão (não avisa o servidor). */
	public static void stopLocal() {
		HelperSession h = helper;
		helper = null;
		activePos = null;
		if (h != null) h.stop();
	}

	public static void sendKey(boolean down, GameKey key) {
		HelperSession h = helper;
		if (h != null) h.sendKey(down, key);
	}

	// ------------------------------------------------------------------ tick (thread principal)

	public static void clientTick(MinecraftClient mc) {
		if (mc.world == null) return;
		if (++tickCounter % 20 == 0) MonitorTextures.tickCleanup();
		BlockPos pos = activePos;
		if (pos == null) return;

		// batimento: mantém o servidor sabendo que a sessão está viva enquanto ainda não há quadros
		if (tickCounter % 100 == 0 && helper != null) {
			report(pos, lastState, lastMessage);
		}
		// o servidor encerrou a sessão (queda de sinal, PC quebrado, parada por outro meio)?
		if (System.currentTimeMillis() - startedAtMs > 3000 && mc.world.isChunkLoaded(pos)) {
			UUID me = mc.player == null ? null : mc.player.getUuid();
			if (mc.world.getBlockEntity(pos) instanceof PcBlockEntity pc) {
				boolean mine = me != null && me.equals(pc.owner()) && pc.pcState().sessionActive();
				if (!mine) stopLocal();
			} else {
				stopLocal();
			}
		}
	}

	public static void onDisconnect() {
		stopLocal();
		MonitorTextures.clearAll();
	}

	/** O servidor respondeu "Session(stop)". */
	public static void onSessionStop(BlockPos pos) {
		if (pos.equals(activePos)) stopLocal();
	}

	public static PcState stateOf(PcBlockEntity pc) { return pc.pcState(); }
}
