package com.minetouhou.block;

import com.minetouhou.MineTouhou;
import com.minetouhou.MthRegistry;
import com.minetouhou.core.TokenBucket;
import com.minetouhou.net.MthPayloads;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Estado persistente do PC: CD armazenado, estado de funcionamento e dono da sessão.
 * Toda mutação acontece na thread do servidor (os handlers de rede do Fabric rodam nela), então
 * inserir/ejetar o CD é atômico e não há como duplicá-lo ou perdê-lo.
 */
public class PcBlockEntity extends BlockEntity {
	private static final int BOOT_TICKS = 40;
	/** Sem sinal do jogador responsável por tanto tempo => a sessão cai em ERRO (ex.: jogo/cliente travou). */
	private static final int SIGNAL_TIMEOUT_TICKS = 20 * 15;

	private ItemStack cd = ItemStack.EMPTY;
	private PcState pcState = PcState.OFF;
	private @Nullable UUID owner;
	private String ownerName = "";
	private String message = "";
	private int bootTicks;

	// transitório (não persistido)
	private int lastSignalTick;
	private @Nullable TokenBucket frameBucket;
	private @Nullable TokenBucket byteBucket;

	public PcBlockEntity(BlockPos pos, BlockState state) {
		super(MthRegistry.PC_BLOCK_ENTITY, pos, state);
	}

	// ------------------------------------------------------------ leitura (servidor e cliente)

	public PcState pcState() { return pcState; }
	public boolean hasCd() { return !cd.isEmpty(); }
	public @Nullable UUID owner() { return owner; }
	public String ownerName() { return ownerName; }
	public String message() { return message; }

	// ------------------------------------------------------------ ações (somente servidor)

	public void powerOnIfOff() {
		if (pcState == PcState.OFF) {
			bootTicks = BOOT_TICKS;
			setState(PcState.BOOTING, "Inicializando...");
		}
	}

	/** Insere o CD do jogador. Retorna true se aceitou. */
	public boolean insertCdFrom(ServerPlayerEntity player) {
		if (!cd.isEmpty() || pcState == PcState.OFF || pcState == PcState.BOOTING || pcState.sessionActive()) return false;
		var inv = player.getInventory();
		// preferência: mão principal; depois o resto do inventário principal
		int slot = -1;
		if (inv.getMainHandStack().isOf(MthRegistry.CD_ITEM)) {
			slot = inv.selectedSlot;
		} else {
			for (int i = 0; i < inv.main.size(); i++) {
				if (inv.main.get(i).isOf(MthRegistry.CD_ITEM)) { slot = i; break; }
			}
		}
		if (slot < 0) return false;
		ItemStack taken = inv.main.get(slot).copyWithCount(1);
		inv.main.get(slot).decrement(1);
		cd = taken;
		setState(PcState.READY, "CD inserido.");
		return true;
	}

	/** Devolve o CD ao jogador (ou ao chão perto dele se o inventário estiver cheio). */
	public boolean ejectCdTo(ServerPlayerEntity player) {
		if (cd.isEmpty() || pcState.sessionActive() || pcState == PcState.BOOTING) return false;
		ItemStack out = cd;
		cd = ItemStack.EMPTY;
		player.getInventory().offerOrDrop(out);
		setState(PcState.AWAITING_CD, "CD ejetado.");
		return true;
	}

	/** Retira o CD sem dar a ninguém (usado ao quebrar o bloco; o chamador o joga no mundo). */
	public ItemStack takeCd() {
		ItemStack out = cd;
		cd = ItemStack.EMPTY;
		return out;
	}

	/** Um jogador pede para iniciar a sessão no computador DELE. Retorna true se foi aceito. */
	public boolean tryStartSession(ServerPlayerEntity player, int nowTick) {
		if (pcState != PcState.READY || cd.isEmpty() || owner != null) return false;
		owner = player.getUuid();
		ownerName = player.getGameProfile().getName();
		lastSignalTick = nowTick;
		frameBucket = null;
		byteBucket = null;
		setState(PcState.VERIFYING, "Verificando arquivos e iniciando em " + ownerName + "...");
		return true;
	}

	/** Encerramento normal ou por falha. {@code error} = mensagem de erro, ou null para voltar a READY. */
	public void endSession(@Nullable String error) {
		notifyOwnerStop();
		owner = null;
		ownerName = "";
		frameBucket = null;
		byteBucket = null;
		if (error != null) {
			setState(PcState.ERROR, error);
		} else {
			setState(cd.isEmpty() ? PcState.AWAITING_CD : PcState.READY, "Sessão encerrada.");
		}
	}

	/** Usado quando o bloco some: não mexe no mundo. */
	public void endSessionSilently() {
		notifyOwnerStop();
		owner = null;
		pcState = PcState.OFF;
	}

	/** Pede ao cliente do jogador responsável (se estiver online) que encerre o helper local. */
	private void notifyOwnerStop() {
		if (owner == null || !(world instanceof ServerWorld sw)) return;
		ServerPlayerEntity p = sw.getServer().getPlayerManager().getPlayer(owner);
		if (p != null && ServerPlayNetworking.canSend(p, MthPayloads.Session.ID)) {
			ServerPlayNetworking.send(p, new MthPayloads.Session(pos, false));
		}
	}

	public void resetFromError() {
		if (pcState == PcState.ERROR && owner == null) {
			setState(cd.isEmpty() ? PcState.AWAITING_CD : PcState.READY, "");
		}
	}

	/** Relatório de estado vindo do jogador responsável (já validado quanto ao remetente). */
	public void applyOwnerReport(String state, String msg, int nowTick) {
		lastSignalTick = nowTick;
		switch (state) {
			case "RUNNING" -> { if (pcState.sessionActive()) setState(PcState.RUNNING, msg); }
			case "VERIFYING" -> { if (pcState.sessionActive()) setState(PcState.VERIFYING, msg); }
			case "ERROR" -> endSession(msg.isBlank() ? "Erro no jogo." : msg);
			case "READY" -> endSession(null);
			default -> { }
		}
	}

	public void touchSignal(int nowTick) { lastSignalTick = nowTick; }

	public boolean allowFrame(int bytes, int viewers, int maxFps, int maxBytesPerSecond, long nowMs) {
		if (frameBucket == null) frameBucket = new TokenBucket(Math.max(2, maxFps), Math.max(1, maxFps), nowMs);
		if (byteBucket == null) byteBucket = new TokenBucket(maxBytesPerSecond, maxBytesPerSecond, nowMs);
		if (!frameBucket.tryConsume(1, nowMs)) return false;
		return byteBucket.tryConsume((double) bytes * Math.max(1, viewers), nowMs);
	}

	private void setState(PcState next, String msg) {
		pcState = next;
		message = msg == null ? "" : msg;
		markDirty();
		if (world != null && !world.isClient) {
			BlockState bs = getCachedState();
			if (bs.get(PcBlock.STATE) != next) {
				world.setBlockState(pos, bs.with(PcBlock.STATE, next), 3);
			}
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, PcBlockEntity pc) {
		int now = (int) (world.getTime() & 0x7FFFFFFF);
		if (state.get(PcBlock.STATE) != pc.pcState) {
			world.setBlockState(pos, state.with(PcBlock.STATE, pc.pcState), 3); // reconcilia após carregar o chunk
		}
		if (pc.pcState.sessionActive() && pc.owner == null) {
			pc.endSession(null);
		}
		if (pc.pcState == PcState.BOOTING && --pc.bootTicks <= 0) {
			pc.setState(pc.cd.isEmpty() ? PcState.AWAITING_CD : PcState.READY,
				pc.cd.isEmpty() ? "Aguardando CD." : "CD detectado.");
		}
		if (pc.pcState.sessionActive() && pc.owner != null && world.getTime() % 20 == 0) {
			var server = world.getServer();
			boolean ownerOnline = server != null && server.getPlayerManager().getPlayer(pc.owner) != null;
			if (!ownerOnline) {
				MineTouhou.LOGGER.info("Sessão em {} encerrada: o jogador responsável saiu.", pos);
				pc.endSession("O jogador responsável desconectou.");
			} else if (now - pc.lastSignalTick > SIGNAL_TIMEOUT_TICKS) {
				MineTouhou.LOGGER.info("Sessão em {} sem sinal por 15 s.", pos);
				pc.endSession("Sem sinal do jogador responsável (o jogo ou o cliente travou).");
			}
		}
	}

	// ------------------------------------------------------------ NBT e sincronização

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (!cd.isEmpty()) nbt.put("cd", cd.encode(registries));
		nbt.putString("pcState", pcState.asString());
		if (owner != null) nbt.putUuid("owner", owner);
		nbt.putString("ownerName", ownerName);
		nbt.putString("message", message);
		nbt.putInt("bootTicks", bootTicks);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		cd = nbt.contains("cd") ? ItemStack.fromNbt(registries, nbt.get("cd")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		pcState = PcState.byId(nbt.getString("pcState"));
		owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
		ownerName = nbt.getString("ownerName");
		message = nbt.getString("message");
		bootTicks = nbt.getInt("bootTicks");
		// Não corrige sessões aqui: readNbt também roda no cliente. O tick do servidor encerra sessões
		// cujo dono não está online (ex.: depois de reiniciar o servidor).
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
