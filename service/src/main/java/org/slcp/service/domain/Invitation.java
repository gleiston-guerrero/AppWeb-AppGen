package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Invitacion a formar parte del equipo de un proyecto.
 *
 * <p>Realiza INV-01 a INV-05. Del enlace se guarda solo su resumen: quien lea
 * la tabla no obtiene nada utilizable.</p>
 */
@Entity
@Table(name = "invitations")
public class Invitation {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "email", nullable = false, updatable = false, length = 254)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "project_role", nullable = false, updatable = false, length = 20)
	private ProjectRole projectRole;

	@Column(name = "invited_by", nullable = false, updatable = false)
	private UUID invitedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason", length = 200)
	private String revokedReason;

	protected Invitation() {
	}

	public static Invitation emitir(String tokenHash, UUID projectId, String email,
			ProjectRole rol, UUID invitedBy, Instant momento, Instant caducidad) {
		Invitation i = new Invitation();
		i.id = UUID.randomUUID();
		i.tokenHash = tokenHash;
		i.projectId = projectId;
		i.email = email.trim().toLowerCase(Locale.ROOT);
		i.projectRole = rol;
		i.invitedBy = invitedBy;
		i.createdAt = momento;
		i.expiresAt = caducidad;
		return i;
	}

	/** Vigente: ni consumida, ni revocada, ni caducada. */
	public boolean estaVigente(Instant momento) {
		return consumedAt == null && revokedAt == null && momento.isBefore(expiresAt);
	}

	/**
	 * Marca la invitacion como usada. Es de un solo uso (INV-01).
	 *
	 * @throws IllegalStateException si ya no estaba vigente
	 */
	public void consumir(Instant momento) {
		if (!estaVigente(momento)) {
			throw new IllegalStateException("La invitacion ya no esta vigente");
		}
		this.consumedAt = momento;
	}

	public void revocar(Instant momento, String motivo) {
		if (revokedAt == null && consumedAt == null) {
			this.revokedAt = momento;
			this.revokedReason = motivo;
		}
	}

	/** Motivo por el que no puede usarse, o vacio si puede. */
	public String motivoDeRechazo(Instant momento) {
		if (consumedAt != null) {
			return "Ese enlace ya se uso. Pida al facilitador que le invite de nuevo";
		}
		if (revokedAt != null) {
			return "Esa invitacion fue retirada por el facilitador del proyecto";
		}
		if (!momento.isBefore(expiresAt)) {
			return "Ese enlace caduco. Pida al facilitador que le invite de nuevo";
		}
		return "";
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getEmail() {
		return email;
	}

	public ProjectRole getProjectRole() {
		return projectRole;
	}

	public UUID getInvitedBy() {
		return invitedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}
}
