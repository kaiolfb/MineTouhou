package com.minetouhou;

import com.minetouhou.core.DataDirs;
import com.minetouhou.core.MthConfig;
import com.minetouhou.loot.MthLoot;
import com.minetouhou.server.ServerSessions;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entrada comum (cliente e servidor). Não toca em classes de cliente. */
public class MineTouhou implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("MineTouhou");

	/** Pasta de dados do usuário (minetouhou/). */
	public static volatile Path ROOT = DataDirs.resolveRoot();
	public static volatile MthConfig CONFIG = new MthConfig();

	@Override
	public void onInitialize() {
		try {
			DataDirs.ensure(ROOT);
			Path cfgFile = DataDirs.config(ROOT).resolve("minetouhou.properties");
			new MthConfig().writeDefaultIfMissing(cfgFile);
			CONFIG = MthConfig.load(cfgFile);
			LOGGER.info("Pasta de dados: {}", ROOT);
		} catch (IOException | RuntimeException e) {
			// Sem permissão de escrita etc.: o mod segue com os padrões e o cliente mostra o erro na tela do PC.
			LOGGER.error("Não foi possível preparar a pasta de dados {}: {}", ROOT, e.toString());
		}
		MthRegistry.init();
		MthLoot.register(CONFIG);
		ServerSessions.register();
	}
}
