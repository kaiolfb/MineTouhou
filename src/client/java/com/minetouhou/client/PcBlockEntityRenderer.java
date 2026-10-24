package com.minetouhou.client;

import com.minetouhou.block.PcBlock;
import com.minetouhou.block.PcBlockEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Desenha o quadro real do jogo sobre a tela do monitor (a tela estática do modelo fica por baixo). */
public class PcBlockEntityRenderer implements BlockEntityRenderer<PcBlockEntity> {
	// Retângulo da tela no modelo (norte), em pixels de bloco (1/16). Ver tools/gen_assets.py: [6,7,4.9]-[15,13.75,4.9].
	private static final float X0 = 6f / 16f;
	private static final float X1 = 15f / 16f;
	private static final float Y0 = 7f / 16f;
	private static final float Y1 = 13.75f / 16f;
	private static final float Z = 4.85f / 16f; // um fio à frente do plano do modelo, contra z-fighting

	public PcBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

	@Override
	public void render(PcBlockEntity pc, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
		int light, int overlay) {
		Identifier tex = MonitorTextures.textureFor(pc.getPos());
		if (tex == null) return;
		int yRot = PcBlock.blockstateYRotation(pc.getCachedState().get(PcBlock.FACING));

		matrices.push();
		matrices.translate(0.5, 0.0, 0.5);
		matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yRot));
		matrices.translate(-0.5, 0.0, -0.5);
		MatrixStack.Entry e = matrices.peek();
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
		// Visto de frente (de quem olha para o sul), o lado esquerdo da tela é o x maior.
		vertex(vc, e, X1, Y1, 0f, 0f);
		vertex(vc, e, X1, Y0, 0f, 1f);
		vertex(vc, e, X0, Y0, 1f, 1f);
		vertex(vc, e, X0, Y1, 1f, 0f);
		matrices.pop();
	}

	private static void vertex(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float u, float v) {
		vc.vertex(e, x, y, Z)
			.color(255, 255, 255, 255)
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(240, 240) // brilho máximo: a tela "emite luz"
			.normal(e, 0f, 0f, -1f);
	}
}
