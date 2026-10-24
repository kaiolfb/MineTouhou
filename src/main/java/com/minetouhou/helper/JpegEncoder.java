package com.minetouhou.helper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Codifica JPEG ajustando a qualidade para caber no limite de bytes do pacote de rede. */
public final class JpegEncoder {
	private final ImageWriter writer;
	private float quality;
	private final float minQuality;
	private final float maxQuality;

	public JpegEncoder(float startQuality, float minQuality, float maxQuality) throws IOException {
		var it = ImageIO.getImageWritersByFormatName("jpeg");
		if (!it.hasNext()) throw new IOException("Nenhum codificador JPEG disponível nesta JVM.");
		this.writer = it.next();
		this.minQuality = minQuality;
		this.maxQuality = maxQuality;
		this.quality = Math.max(minQuality, Math.min(maxQuality, startQuality));
	}

	public float quality() {
		return quality;
	}

	/**
	 * Codifica a imagem; se passar de {@code maxBytes}, reduz a qualidade e tenta de novo (até o mínimo).
	 * Retorna null se mesmo na qualidade mínima não couber (o quadro é descartado).
	 * Quando cabe com folga, sobe devagar a qualidade para o próximo quadro.
	 */
	public byte[] encode(BufferedImage img, int maxBytes) throws IOException {
		float q = quality;
		while (true) {
			byte[] out = encodeAt(img, q);
			if (out.length <= maxBytes) {
				quality = q;
				if (out.length < maxBytes * 0.6f) {
					quality = Math.min(maxQuality, q + 0.02f);
				}
				return out;
			}
			if (q <= minQuality + 1e-4f) {
				quality = minQuality;
				return null;
			}
			q = Math.max(minQuality, q - 0.1f);
		}
	}

	private byte[] encodeAt(BufferedImage img, float q) throws IOException {
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(q);
		ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
		try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(img, null, null), param);
		}
		return bos.toByteArray();
	}

	public void close() {
		writer.dispose();
	}
}
