package com.minetouhou.core;

/** Validações baratas de quadros JPEG, feitas antes de retransmitir ou decodificar dados da rede. */
public final class FrameCheck {
	public static final int MAX_WIDTH = 640;
	public static final int MAX_HEIGHT = 480;

	private FrameCheck() {}

	/** Começa com SOI (FF D8) e termina com EOI (FF D9). */
	public static boolean looksLikeJpeg(byte[] d) {
		return d != null && d.length > 4
			&& (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8
			&& (d[d.length - 2] & 0xFF) == 0xFF && (d[d.length - 1] & 0xFF) == 0xD9;
	}

	/**
	 * Lê largura/altura do marcador SOFn sem decodificar a imagem.
	 * Retorna {largura, altura} ou null se não achar um cabeçalho válido.
	 */
	public static int[] jpegSize(byte[] d) {
		if (!looksLikeJpeg(d)) return null;
		int i = 2;
		while (i + 3 < d.length) {
			if ((d[i] & 0xFF) != 0xFF) return null;
			int marker = d[i + 1] & 0xFF;
			if (marker == 0xFF) { i++; continue; }
			if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) { i += 2; continue; }
			if (marker == 0xD9 || marker == 0xDA) return null; // chegou aos dados sem achar SOF
			int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
			if (len < 2) return null;
			boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
			if (sof) {
				if (i + 8 >= d.length) return null;
				int h = ((d[i + 5] & 0xFF) << 8) | (d[i + 6] & 0xFF);
				int w = ((d[i + 7] & 0xFF) << 8) | (d[i + 8] & 0xFF);
				return new int[] {w, h};
			}
			i += 2 + len;
		}
		return null;
	}

	/**
	 * Valida um quadro recebido. O tamanho DECLARADO precisa bater com o do cabeçalho JPEG
	 * e respeitar os limites, o que também protege contra "bombas de descompressão".
	 */
	public static boolean isAcceptable(byte[] jpeg, int declaredW, int declaredH, int maxBytes) {
		if (jpeg == null || jpeg.length == 0 || jpeg.length > maxBytes) return false;
		if (declaredW < 16 || declaredH < 16 || declaredW > MAX_WIDTH || declaredH > MAX_HEIGHT) return false;
		int[] size = jpegSize(jpeg);
		return size != null && size[0] == declaredW && size[1] == declaredH;
	}
}
