package com.minetouhou.block;

import net.minecraft.util.StringIdentifiable;

/** Estados de funcionamento do PC (espelhados na propriedade de blockstate "state" para trocar a textura da tela). */
public enum PcState implements StringIdentifiable {
	OFF("off"),
	BOOTING("booting"),
	AWAITING_CD("awaiting_cd"),
	/** Verificando arquivos e iniciando o jogo na máquina do jogador responsável. */
	VERIFYING("verifying"),
	READY("ready"),
	RUNNING("running"),
	ERROR("error");

	private final String id;

	PcState(String id) {
		this.id = id;
	}

	@Override
	public String asString() {
		return id;
	}

	public static PcState byId(String id) {
		for (PcState s : values()) {
			if (s.id.equals(id)) return s;
		}
		return OFF;
	}

	public boolean sessionActive() {
		return this == VERIFYING || this == RUNNING;
	}
}
