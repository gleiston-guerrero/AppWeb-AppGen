package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Enlace de recuperacion de acceso.
 *
 * <p>De vida deliberadamente corta, mas que la de una invitacion: quien recibe
 * una invitacion puede tardar dias en atenderla, mientras que quien pide
 * recuperar su acceso lo esta haciendo en ese momento. Cada minuto adicional de
 * validez es tiempo en que un enlace filtrado sigue sirviendo.</p>
 */
@Entity
@Table(name = "password_resets")
public class PasswordReset {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "origin", nullable = false, updatable = false, length = 60)
	private String origin;

	protected PasswordReset() {
	}

	public static PasswordReset emitir(String tokenHash, UUID userId, String origin,
			Instant momento, Instant caducidad) {
		PasswordReset pr = new PasswordReset();
		pr.id = UUID.randomUUID();
		pr.tokenHash = tokenHash;
		pr.userId = userId;
		pr.requestedAt = momento;
		pr.expiresAt = caducidad;
		pr.origin = origin == null ? "desconocido" : origin;
		return pr;
	}

	public boolean estaVigente(Instant momento) {
		return usedAt == null && revokedAt == null && momento.isBefore(expiresAt);
	}

	public void consumir(Instant momento) {
		if (!estaVigente(momento)) {
			throw new IllegalStateException("El enlace ya no esta vigente");
		}
		this.usedAt = momento;
	}

	/**
	 * Retira el enlace.
	 *
	 * <p>Se invocan al emitir uno nuevo: pedir la recuperacion otra vez debe
	 * invalidar el anterior, o quedarian varios enlaces validos a la vez y bastaria
	 * con que se filtrase cualquiera de ellos.</p>
	 */
	public void revocar(Instant momento) {
		if (revokedAt == null && usedAt == null) {
			this.revokedAt = momento;
		}
	}

	/** Motivo por el que no sirve, o vacio si sirve. */
	public String motivoDeRechazo(Instant momento) {
		if (usedAt != null) {
			return "Ese enlace ya se uso. Solicite otro si necesita cambiar su contrasena";
		}
		if (revokedAt != null) {
			return "Ese enlace quedo sin efecto al solicitarse otro. Use el mas reciente";
		}
		if (!momento.isBefore(expiresAt)) {
			return "Ese enlace caduco. Solicite otro";
		}
		return "";
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
