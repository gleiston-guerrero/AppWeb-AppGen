package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Membresia de una persona en el equipo de un proyecto.
 *
 * <p>Es la unidad de autorizacion: quien pregunta que puede hacer alguien,
 * pregunta por su membresia en el proyecto concreto, no por su cuenta.</p>
 */
@Entity
@Table(name = "project_memberships")
public class ProjectMembership {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "project_role", nullable = false, updatable = false, length = 20)
	private ProjectRole projectRole;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private MembershipStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProjectMembership() {
	}

	public static ProjectMembership activa(UUID projectId, UUID userId, ProjectRole rol, Instant momento) {
		ProjectMembership m = new ProjectMembership();
		m.id = UUID.randomUUID();
		m.projectId = projectId;
		m.userId = userId;
		m.projectRole = rol;
		m.status = MembershipStatus.ACTIVE;
		m.createdAt = momento;
		return m;
	}

	public void retirar() {
		this.status = MembershipStatus.DECOMMISSIONED;
	}

	public boolean estaActiva() {
		return status == MembershipStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public UUID getUserId() {
		return userId;
	}

	public ProjectRole getProjectRole() {
		return projectRole;
	}

	public MembershipStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
