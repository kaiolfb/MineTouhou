package com.minetouhou.client;

import com.minetouhou.MineTouhou;
import com.minetouhou.block.PcBlockEntity;
import com.minetouhou.block.PcState;
import com.minetouhou.core.DataDirs;
import com.minetouhou.core.GameInstall;
import com.minetouhou.net.MthPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Interface do computador. Todos os botões executam ações reais (rede/servidor ou ação local). */
public class PcScreen extends Screen {
	private static final int PANEL_W = 300;
	private static final int PANEL_H = 214;

	private final BlockPos pos;
	private ButtonWidget cdButton;
	private ButtonWidget verifyButton;
	private ButtonWidget startButton;
	private ButtonWidget playButton;
	private ButtonWidget stopButton;
	private ButtonWidget configButton;
	private String configNote = "";

	public PcScreen(BlockPos pos) {
		super(Text.literal("PC MineTouhou"));
		this.pos = pos;
	}

	private @Nullable PcBlockEntity pc() {
		var w = MinecraftClient.getInstance().world;
		return w != null && w.getBlockEntity(pos) instanceof PcBlockEntity p ? p : null;
	}

	private static void send(int action, BlockPos pos) {
		ClientPlayNetworking.send(new MthPayloads.Action(pos, action));
	}

	@Override
	protected void init() {
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		int bw = 94;
		int bh = 20;
		int y1 = top + PANEL_H - 70;
		int y2 = top + PANEL_H - 46;
		int y3 = top + PANEL_H - 22;

		cdButton = addDrawableChild(ButtonWidget.builder(Text.literal("Inserir CD"), b -> {
			PcBlockEntity p = pc();
			if (p != null) send(p.hasCd() ? MthPayloads.Action.EJECT_CD : MthPayloads.Action.INSERT_CD, pos);
		}).dimensions(left + 8, y1, bw, bh).build());

		verifyButton = addDrawableChild(ButtonWidget.builder(Text.literal("Verificar arquivos"),
			b -> ClientSessions.verifyAsync()).dimensions(left + 8 + bw + 4, y1, bw, bh).build());

		startButton = addDrawableChild(ButtonWidget.builder(Text.literal("Iniciar Touhou 6"),
			b -> send(MthPayloads.Action.START, pos)).dimensions(left + 8 + 2 * (bw + 4), y1, bw, bh).build());

		playButton = addDrawableChild(ButtonWidget.builder(Text.literal("Jogar"), b -> {
			if (ClientSessions.isActiveFor(pos)) MinecraftClient.getInstance().setScreen(new GameScreen(pos));
		}).dimensions(left + 8, y2, bw, bh).build());

		stopButton = addDrawableChild(ButtonWidget.builder(Text.literal("Encerrar jogo"), b -> {
			PcBlockEntity p = pc();
			if (p != null && p.pcState() == PcState.ERROR) {
				send(MthPayloads.Action.RESET, pos);
			} else {
				ClientSessions.onSessionStop(pos);
				send(MthPayloads.Action.STOP, pos);
			}
		}).dimensions(left + 8 + bw + 4, y2, bw, bh).build());

		configButton = addDrawableChild(ButtonWidget.builder(Text.literal("Configuração"), b -> {
			try {
				Util.getOperatingSystem().open(DataDirs.config(MineTouhou.ROOT));
				configNote = "Abrindo: " + DataDirs.config(MineTouhou.ROOT).resolve("minetouhou.properties");
			} catch (RuntimeException e) {
				configNote = "Edite: " + DataDirs.config(MineTouhou.ROOT).resolve("minetouhou.properties");
			}
		}).dimensions(left + 8 + 2 * (bw + 4), y2, bw, bh).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Fechar"), b -> close())
			.dimensions(left + PANEL_W / 2 - 47, y3, bw, bh).build());
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static String stateLabel(PcState s) {
		return switch (s) {
			case OFF -> "Desligado";
			case BOOTING -> "Inicializando";
			case AWAITING_CD -> "Aguardando CD";
			case VERIFYING -> "Verificando arquivos / iniciando";
			case READY -> "Pronto";
			case RUNNING -> "Executando";
			case ERROR -> "Erro";
		};
	}

	@Override
	public void tick() {
		PcBlockEntity p = pc();
		if (p == null) {
			close(); // o PC foi quebrado
			return;
		}
		PcState s = p.pcState();
		var me = client == null || client.player == null ? null : client.player.getUuid();
		boolean mine = me != null && me.equals(p.owner());
		cdButton.setMessage(Text.literal(p.hasCd() ? "Ejetar CD" : "Inserir CD"));
		cdButton.active = !s.sessionActive() && s != PcState.OFF && s != PcState.BOOTING && s != PcState.ERROR;
		verifyButton.active = !ClientSessions.verifying;
		startButton.active = s == PcState.READY && p.hasCd();
		playButton.active = mine && s == PcState.RUNNING && ClientSessions.isActiveFor(pos);
		stopButton.setMessage(Text.literal(s == PcState.ERROR ? "Limpar erro" : "Encerrar jogo"));
		stopButton.active = (s.sessionActive() && mine) || s == PcState.ERROR;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx, mouseX, mouseY, delta);
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		ctx.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, 0xFF1B2A3A);
		ctx.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF0B1030);
		ctx.fill(left, top, left + PANEL_W, top + 14, 0xFF1233A8);
		ctx.drawTextWithShadow(textRenderer, "MineTouhou OS", left + 6, top + 3, 0xFFFFFFFF);

		PcBlockEntity p = pc();
		List<String> lines = new ArrayList<>();
		if (p != null) {
			var me = client == null || client.player == null ? null : client.player.getUuid();
			boolean mine = me != null && me.equals(p.owner());
			lines.add("Estado do PC: " + stateLabel(p.pcState()));
			lines.add("CD: " + (p.hasCd() ? "MineTouhou (inserido)" : "vazio"));
			GameInstall.Result r = ClientSessions.lastVerify;
			lines.add("Jogo: " + (r == null ? "não verificado" : r.gameName()));
			lines.add("Versão: " + (r == null ? "-" : r.version()));
			if (ClientSessions.verifying) {
				lines.add("Verificação: em andamento...");
			} else if (r == null) {
				lines.add("Verificação: clique em \"Verificar arquivos\"");
			} else {
				lines.add("Verificação: " + (r.valid() ? "OK" : "FALHOU"));
				for (String e : r.errors()) lines.add(" - " + e);
				for (String w : r.warnings()) lines.add(" ! " + w);
			}
			String net;
			if (p.owner() == null) {
				net = "Sem sessão ativa";
			} else if (mine) {
				net = "Você é o jogador responsável (transmitindo)";
			} else {
				net = "Assistindo " + p.ownerName() + (MonitorTextures.hasRecentFrame(pos) ? " (recebendo imagem)" : " (sem imagem)");
			}
			lines.add("Multiplayer: " + net);
			if (!p.message().isBlank()) lines.add(p.message());
			if (p.pcState() == PcState.ERROR) lines.add("Veja logs/helper.log e o LEIA-ME.txt em " + MineTouhou.ROOT);
		}
		if (!configNote.isEmpty()) lines.add(configNote);

		int y = top + 20;
		int maxW = PANEL_W - 12;
		for (String line : lines) {
			for (var seg : textRenderer.wrapLines(Text.literal(line), maxW)) {
				if (y > top + PANEL_H - 76) break;
				ctx.drawText(textRenderer, seg, left + 6, y, 0xFFD6E4FF, false);
				y += 10;
			}
		}
		super.render(ctx, mouseX, mouseY, delta);
	}
}
