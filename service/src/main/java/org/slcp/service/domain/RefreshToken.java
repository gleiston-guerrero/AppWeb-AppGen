package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Token de renovacion de sesion.
 *
 * <p>Realiza SEC-03. No se almacena el token sino su huella: quien lea la tabla
 * no obtiene nada utilizable. La revocacion se registra y la fila permanece,
 * de modo que el cierre de sesion deja rastro.</p>
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason", length = 40)
	private String revokedReason;

	protected RefreshToken() {
	}

	public static RefreshToken emitir(String tokenHash, UUID userId, Instant momento, Instant caducidad) {
		RefreshToken token = new RefreshToken();
		token.id = UUID.randomUUID();
		token.tokenHash = tokenHash;
		token.userId = userId;
		token.issuedAt = momento;
		token.expiresAt = caducidad;
		return token;
	}

	/** Revoca el token. La base de datos impide devolverlo despues a vigente. */
	public void revocar(Instant momento, String motivo) {
		if (revokedAt == null) {
			this.revokedAt = momento;
			this.revokedReason = motivo;
		}
	}

	public boolean estaVigente(Instant momento) {
		return revokedAt == null && momento.isBefore(expiresAt);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
