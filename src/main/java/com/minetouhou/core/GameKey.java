package com.minetouhou.core;

import java.util.Locale;

/**
 * Lista fechada de teclas que o helper aceita encaminhar ao jogo. Qualquer outro nome é recusado,
 * de modo que o mod não consegue pedir ao helper nada além de teclas do Touhou 6.
 */
public enum GameKey {
	UP("Up"),
	DOWN("Down"),
	LEFT("Left"),
	RIGHT("Right"),
	Z("z"),
	X("x"),
	SHIFT("Shift_L"),
	CTRL("Control_L"),
	ESC("Escape"),
	ENTER("Return"),
	Q("q");

	/** Nome do keysym do X11 (usado pelo xdotool). */
	public final String x11Keysym;

	GameKey(String x11Keysym) {
		this.x11Keysym = x11Keysym;
	}

	public static GameKey parse(String name) {
		if (name == null) return null;
		try {
			return valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
