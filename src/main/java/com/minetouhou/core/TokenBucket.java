package com.minetouhou.core;

/**
 * Balde de fichas simples, usado para limitar a taxa de quadros e de bytes por sessão.
 * Não depende de relógio global: o chamador informa o tempo (facilita os testes).
 */
public final class TokenBucket {
	private final double capacity;
	private final double refillPerSecond;
	private double tokens;
	private long lastMs;

	public TokenBucket(double capacity, double refillPerSecond, long nowMs) {
		if (capacity <= 0 || refillPerSecond <= 0) {
			throw new IllegalArgumentException("capacity e refillPerSecond devem ser positivos");
		}
		this.capacity = capacity;
		this.refillPerSecond = refillPerSecond;
		this.tokens = capacity;
		this.lastMs = nowMs;
	}

	/** Tenta consumir {@code amount} fichas. Retorna false (sem consumir) se não houver o bastante. */
	public synchronized boolean tryConsume(double amount, long nowMs) {
		refill(nowMs);
		if (amount > tokens) {
			return false;
		}
		tokens -= amount;
		return true;
	}

	private void refill(long nowMs) {
		long dt = nowMs - lastMs;
		if (dt > 0) {
			tokens = Math.min(capacity, tokens + dt / 1000.0 * refillPerSecond);
			lastMs = nowMs;
		} else if (dt < 0) {
			lastMs = nowMs; // relógio andou para trás: apenas reancora
		}
	}
}
