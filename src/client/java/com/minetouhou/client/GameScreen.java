package com.minetouhou.client;

import com.minetouhou.MineTouhou;
import com.minetouhou.core.GameKey;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Modo de jogo: uma tela cheia que recebe TODAS as teclas (o Minecraft não vê nenhuma), mostra o quadro real
 * do jogo e encaminha as teclas permitidas ao helper. Fechar a tela devolve os controles normais.
 */
public class GameScreen extends Screen {
	private final BlockPos pos;
	private final Set<GameKey> pressed = EnumSet.noneOf(GameKey.class);

	public GameScreen(BlockPos pos) {
		super(Text.literal("MineTouhou"));
		this.pos = pos;
	}

	@Override
	public boolean shouldPause() {
		return false; // o servidor/mundo continua rodando (multiplayer; o jogo roda em tempo real)
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // Esc é uma tecla do jogo (pausa); a saída é a tecla configurada (padrão F10)
	}

	private static GameKey map(int glfw) {
		return switch (glfw) {
			case GLFW.GLFW_KEY_UP -> GameKey.UP;
			case GLFW.GLFW_KEY_DOWN -> GameKey.DOWN;
			case GLFW.GLFW_KEY_LEFT -> GameKey.LEFT;
			case GLFW.GLFW_KEY_RIGHT -> GameKey.RIGHT;
			case GLFW.GLFW_KEY_Z -> GameKey.Z;
			case GLFW.GLFW_KEY_X -> GameKey.X;
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> GameKey.SHIFT;
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> GameKey.CTRL;
			case GLFW.GLFW_KEY_ESCAPE -> GameKey.ESC;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> GameKey.ENTER;
			case GLFW.GLFW_KEY_Q -> GameKey.Q;
			default -> null;
		};
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == MineTouhou.CONFIG.exitKey) {
			close();
			return true;
		}
		GameKey k = map(keyCode);
		if (k != null && pressed.add(k)) { // ignora repetição automática da tecla segurada
			ClientSessions.sendKey(true, k);
		}
		return true; // engole tudo: nenhum atalho do Minecraft é acionado
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		GameKey k = map(keyCode);
		if (k != null && pressed.remove(k)) ClientSessions.sendKey(false, k);
		return true;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return true;
	}

	@Override
	public void tick() {
		if (!ClientSessions.isActiveFor(pos)) close(); // jogo terminou ou sessão caiu
	}

	@Override
	public void removed() {
		for (GameKey k : pressed) ClientSessions.sendKey(false, k); // nunca deixa tecla "presa" no jogo
		pressed.clear();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xFF000000);
		Identifier tex = MonitorTextures.textureFor(pos);
		int tw = MonitorTextures.widthOf(pos);
		int th = MonitorTextures.heightOf(pos);
		if (tex != null && tw > 0 && th > 0) {
			// mantém 4:3
			int h = Math.min(height - 24, (width - 16) * 3 / 4);
			int w = h * 4 / 3;
			int x = (width - w) / 2;
			int y = (height - h) / 2;
			ctx.drawTexture(tex, x, y, w, h, 0f, 0f, tw, th, tw, th);
		} else {
			ctx.drawCenteredTextWithShadow(textRenderer, "Aguardando imagem do jogo...", width / 2, height / 2, 0xFFAAAAAA);
		}
		String exit = "Sair: " + GLFW.glfwGetKeyName(MineTouhou.CONFIG.exitKey, 0);
		String label = GLFW.glfwGetKeyName(MineTouhou.CONFIG.exitKey, 0) == null ? "tecla " + MineTouhou.CONFIG.exitKey
			: exit;
		if (MineTouhou.CONFIG.exitKey == GLFW.GLFW_KEY_F10) label = "Sair: F10";
		ctx.drawTextWithShadow(textRenderer, label + "  |  setas, Z, X, Shift, Esc", 6, height - 12, 0xFF888888);
	}
}
