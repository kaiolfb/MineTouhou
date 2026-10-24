package com.minetouhou.net;

import com.minetouhou.MthRegistry;
import com.minetouhou.core.MthConfig;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Pacotes de rede do MineTouhou. Nenhum carrega comandos nem caminhos: só ids de ação, estado e imagem.
 * Todos os campos de tamanho variável têm limite no próprio codec, então um cliente malicioso não consegue
 * forçar alocações grandes no servidor.
 */
public final class MthPayloads {
	private MthPayloads() {}

	/** Jogador -> servidor: ação na interface do PC. */
	public record Action(BlockPos pos, int action) implements CustomPayload {
		public static final int INSERT_CD = 0;
		public static final int EJECT_CD = 1;
		public static final int START = 2;
		public static final int STOP = 3;
		public static final int RESET = 4;

		public static final Id<Action> ID = new Id<>(MthRegistry.id("action"));
		public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, Action::pos,
			PacketCodecs.VAR_INT, Action::action,
			Action::new);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Jogador responsável -> servidor: estado do helper local. */
	public record Report(BlockPos pos, String state, String message) implements CustomPayload {
		public static final Id<Report> ID = new Id<>(MthRegistry.id("report"));
		public static final PacketCodec<RegistryByteBuf, Report> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, Report::pos,
			PacketCodecs.string(16), Report::state,
			PacketCodecs.string(200), Report::message,
			Report::new);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Quadro JPEG. Usado nos dois sentidos (jogador -> servidor e servidor -> espectadores). */
	public record Frame(BlockPos pos, int width, int height, byte[] jpeg) implements CustomPayload {
		public static final Id<Frame> ID = new Id<>(MthRegistry.id("frame"));
		public static final PacketCodec<RegistryByteBuf, Frame> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, Frame::pos,
			PacketCodecs.VAR_INT, Frame::width,
			PacketCodecs.VAR_INT, Frame::height,
			PacketCodecs.byteArray(MthConfig.WIRE_MAX_FRAME_BYTES), Frame::jpeg,
			Frame::new);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Frame do servidor para os espectadores (tipo separado: sentidos diferentes, registros diferentes). */
	public record FrameOut(BlockPos pos, int width, int height, byte[] jpeg) implements CustomPayload {
		public static final Id<FrameOut> ID = new Id<>(MthRegistry.id("frame_out"));
		public static final PacketCodec<RegistryByteBuf, FrameOut> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, FrameOut::pos,
			PacketCodecs.VAR_INT, FrameOut::width,
			PacketCodecs.VAR_INT, FrameOut::height,
			PacketCodecs.byteArray(MthConfig.WIRE_MAX_FRAME_BYTES), FrameOut::jpeg,
			FrameOut::new);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Servidor -> jogador responsável: "inicie o helper local para este PC" / "pare". */
	public record Session(BlockPos pos, boolean start) implements CustomPayload {
		public static final Id<Session> ID = new Id<>(MthRegistry.id("session"));
		public static final PacketCodec<RegistryByteBuf, Session> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, Session::pos,
			PacketCodecs.BOOL, Session::start,
			Session::new);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}
}
