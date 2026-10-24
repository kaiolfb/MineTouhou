package com.minetouhou.client;

import com.minetouhou.MthRegistry;
import com.minetouhou.core.FrameCheck;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Uma textura dinâmica por PC. Os JPEGs chegam de qualquer thread, são decodificados numa thread própria
 * (sempre só o quadro mais recente: se o decodificador atrasar, quadros antigos são descartados) e enviados
 * à GPU na thread de renderização. Nunca bloqueia o jogo.
 */
public final class MonitorTextures {
	private static final long STALE_MS = 3000;
	private static final long DESTROY_MS = 30_000;
	private static final int MAX_BYTES = 200_000;

	private static final Map<Long, Entry> ENTRIES = new ConcurrentHashMap<>();
	private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "mth-decoder");
		t.setDaemon(true);
		return t;
	});

	private MonitorTextures() {}

	private static final class Entry {
		final Identifier id;
		final AtomicReference<Pending> pending = new AtomicReference<>();
		final AtomicReference<BufferedImage> decoded = new AtomicReference<>();
		final AtomicBoolean decoding = new AtomicBoolean(false);
		NativeImageBackedTexture texture; // só na thread de renderização
		volatile long lastFrameMs;
		volatile int width;
		volatile int height;

		Entry(long key) {
			this.id = MthRegistry.id("monitor/m" + Long.toUnsignedString(key));
		}
	}

	private record Pending(int width, int height, byte[] jpeg) {}

	/** Qualquer thread. */
	public static void submit(BlockPos pos, int width, int height, byte[] jpeg) {
		if (!FrameCheck.isAcceptable(jpeg, width, height, MAX_BYTES)) return;
		long key = pos.asLong();
		Entry e = ENTRIES.computeIfAbsent(key, Entry::new);
		e.pending.set(new Pending(width, height, jpeg));
		if (e.decoding.compareAndSet(false, true)) {
			DECODER.execute(() -> decodeLoop(e));
		}
	}

	private static void decodeLoop(Entry e) {
		try {
			Pending p;
			while ((p = e.pending.getAndSet(null)) != null) {
				try {
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(p.jpeg()));
					if (img != null && img.getWidth() == p.width() && img.getHeight() == p.height()) {
						e.decoded.set(img);
						MinecraftClient.getInstance().execute(() -> upload(e));
					}
				} catch (Exception | OutOfMemoryError ex) {
					// quadro corrompido: ignora e segue com o próximo
				}
			}
		} finally {
			e.decoding.set(false);
			if (e.pending.get() != null && e.decoding.compareAndSet(false, true)) {
				DECODER.execute(() -> decodeLoop(e));
			}
		}
	}

	/** Thread de renderização. */
	private static void upload(Entry e) {
		BufferedImage img = e.decoded.getAndSet(null);
		if (img == null) return;
		int w = img.getWidth();
		int h = img.getHeight();
		var tm = MinecraftClient.getInstance().getTextureManager();
		if (e.texture == null || e.width != w || e.height != h) {
			if (e.texture != null) tm.destroyTexture(e.id);
			e.texture = new NativeImageBackedTexture(new NativeImage(w, h, false));
			tm.registerTexture(e.id, e.texture);
			e.width = w;
			e.height = h;
		}
		NativeImage ni = e.texture.getImage();
		if (ni == null) return;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = img.getRGB(x, y);
				int abgr = 0xFF000000 | ((argb & 0xFF) << 16) | (argb & 0xFF00) | ((argb >> 16) & 0xFF);
				ni.setColor(x, y, abgr);
			}
		}
		e.texture.upload();
		e.lastFrameMs = System.currentTimeMillis();
	}

	/** Thread de renderização: textura com quadro recente, ou null (o monitor mostra a tela estática do modelo). */
	public static @Nullable Identifier textureFor(BlockPos pos) {
		Entry e = ENTRIES.get(pos.asLong());
		if (e == null || e.texture == null) return null;
		return System.currentTimeMillis() - e.lastFrameMs < STALE_MS ? e.id : null;
	}

	public static int widthOf(BlockPos pos) {
		Entry e = ENTRIES.get(pos.asLong());
		return e == null ? 0 : e.width;
	}

	public static int heightOf(BlockPos pos) {
		Entry e = ENTRIES.get(pos.asLong());
		return e == null ? 0 : e.height;
	}

	/** Há quadro recente? (usado na tela para mostrar "recebendo"/"sem sinal"). */
	public static boolean hasRecentFrame(BlockPos pos) {
		return textureFor(pos) != null;
	}

	/** Thread de renderização, uma vez por tick: libera texturas sem uso. */
	public static void tickCleanup() {
		long now = System.currentTimeMillis();
		var tm = MinecraftClient.getInstance().getTextureManager();
		ENTRIES.entrySet().removeIf(en -> {
			Entry e = en.getValue();
			if (e.texture != null && now - e.lastFrameMs > DESTROY_MS && !e.decoding.get()) {
				tm.destroyTexture(e.id);
				e.texture = null;
				return true;
			}
			return false;
		});
	}

	public static void clearAll() {
		var tm = MinecraftClient.getInstance().getTextureManager();
		for (Entry e : ENTRIES.values()) {
			if (e.texture != null) tm.destroyTexture(e.id);
			e.texture = null;
		}
		ENTRIES.clear();
	}
}
