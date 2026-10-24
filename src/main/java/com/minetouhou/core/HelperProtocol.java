package com.minetouhou.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Protocolo entre o mod (cliente Minecraft) e o processo auxiliar (helper). Ver docs/PROTOCOLO-HELPER.md.
 *
 * <p>helper -> mod (stdout, binário):  {@code [tipo u8][tamanho u32 BE][payload]}
 * <ul>
 *   <li>tipo 1 STATUS: payload UTF-8 {@code "ESTADO|mensagem"}</li>
 *   <li>tipo 2 FRAME:  payload {@code [largura u16][altura u16][bytes JPEG]}</li>
 * </ul>
 * mod -> helper (stdin, texto UTF-8, uma linha por comando): {@code KEY DOWN|UP <NOME>}, {@code PING}, {@code QUIT}.
 */
public final class HelperProtocol {
	public static final int TYPE_STATUS = 1;
	public static final int TYPE_FRAME = 2;
	public static final int MAX_PAYLOAD = 1 << 20;

	private HelperProtocol() {}

	/** Mensagem decodificada do helper. */
	public record Message(int type, String state, String message, int width, int height, byte[] jpeg) {
		public static Message status(String state, String message) {
			return new Message(TYPE_STATUS, state, message, 0, 0, null);
		}

		public static Message frame(int w, int h, byte[] jpeg) {
			return new Message(TYPE_FRAME, null, null, w, h, jpeg);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Message m && type == m.type && width == m.width && height == m.height
				&& java.util.Objects.equals(state, m.state) && java.util.Objects.equals(message, m.message)
				&& Arrays.equals(jpeg, m.jpeg);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(type, state, message, width, height) * 31 + Arrays.hashCode(jpeg);
		}
	}

	/** Comando mod -> helper já validado. */
	public record Command(Kind kind, GameKey key) {
		public enum Kind { KEY_DOWN, KEY_UP, PING, QUIT }
	}

	public static synchronized void writeStatus(OutputStream out, String state, String message) throws IOException {
		byte[] payload = (state + "|" + (message == null ? "" : message)).getBytes(StandardCharsets.UTF_8);
		writeRaw(out, TYPE_STATUS, payload);
	}

	public static synchronized void writeFrame(OutputStream out, int w, int h, byte[] jpeg) throws IOException {
		if (w < 1 || w > 0xFFFF || h < 1 || h > 0xFFFF) throw new IOException("dimensões inválidas");
		byte[] payload = new byte[4 + jpeg.length];
		payload[0] = (byte) (w >> 8);
		payload[1] = (byte) w;
		payload[2] = (byte) (h >> 8);
		payload[3] = (byte) h;
		System.arraycopy(jpeg, 0, payload, 4, jpeg.length);
		writeRaw(out, TYPE_FRAME, payload);
	}

	private static void writeRaw(OutputStream out, int type, byte[] payload) throws IOException {
		if (payload.length > MAX_PAYLOAD) throw new IOException("payload grande demais: " + payload.length);
		DataOutputStream d = new DataOutputStream(out);
		d.writeByte(type);
		d.writeInt(payload.length);
		d.write(payload);
		d.flush();
	}

	/** Lê uma mensagem; retorna null em fim de fluxo limpo. Lança IOException em dados inválidos. */
	public static Message read(InputStream in) throws IOException {
		DataInputStream d = in instanceof DataInputStream di ? di : new DataInputStream(in);
		int type;
		try {
			type = d.readUnsignedByte();
		} catch (EOFException e) {
			return null;
		}
		int len = d.readInt();
		if (len < 0 || len > MAX_PAYLOAD) throw new IOException("tamanho de mensagem inválido: " + len);
		byte[] payload = new byte[len];
		d.readFully(payload);
		switch (type) {
			case TYPE_STATUS -> {
				String s = new String(payload, StandardCharsets.UTF_8);
				int i = s.indexOf('|');
				return i < 0 ? Message.status(s, "") : Message.status(s.substring(0, i), s.substring(i + 1));
			}
			case TYPE_FRAME -> {
				if (len < 4) throw new IOException("frame curto demais");
				int w = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
				int h = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
				return Message.frame(w, h, Arrays.copyOfRange(payload, 4, len));
			}
			default -> throw new IOException("tipo de mensagem desconhecido: " + type);
		}
	}

	/** Interpreta uma linha de comando; retorna null se for inválida (nunca executa nada por conta própria). */
	public static Command parseCommand(String line) {
		if (line == null) return null;
		String[] parts = line.trim().split("\\s+");
		if (parts.length == 0) return null;
		switch (parts[0].toUpperCase(java.util.Locale.ROOT)) {
			case "PING" -> {
				return parts.length == 1 ? new Command(Command.Kind.PING, null) : null;
			}
			case "QUIT" -> {
				return parts.length == 1 ? new Command(Command.Kind.QUIT, null) : null;
			}
			case "KEY" -> {
				if (parts.length != 3) return null;
				GameKey key = GameKey.parse(parts[2]);
				if (key == null) return null;
				return switch (parts[1].toUpperCase(java.util.Locale.ROOT)) {
					case "DOWN" -> new Command(Command.Kind.KEY_DOWN, key);
					case "UP" -> new Command(Command.Kind.KEY_UP, key);
					default -> null;
				};
			}
			default -> {
				return null;
			}
		}
	}

	public static String keyLine(boolean down, GameKey key) {
		return "KEY " + (down ? "DOWN " : "UP ") + key.name();
	}
}
