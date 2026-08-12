package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Proyecto gestionado dentro de la plataforma. Es el nivel N2 de SLCP-DOC-000. */
@Entity
@Table(name = "projects")
public class Project {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "name", nullable = false, length = 160)
	private String name;

	@Column(name = "purpose", nullable = false, length = 1000)
	private String purpose;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ProjectStatus status;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	@Column(name = "version", nullable = false)
	private int version;

	protected Project() {
	}

	public static Project crear(String name, String purpose, UUID createdBy,
			long secuencia, Instant momento) {
		Project p = new Project();
		p.id = UUID.randomUUID();
		p.readableId = String.format("PRJ-%04d-v1", secuencia);
		p.name = name.trim();
		p.purpose = purpose == null ? "" : purpose.trim();
		p.status = ProjectStatus.ACTIVE;
		p.createdBy = createdBy;
		p.createdAt = momento;
		return p;
	}

	public boolean estaActivo() {
		return status == ProjectStatus.ACTIVE;
	}

	/** Modifica los datos del proyecto. */
	public void editar(String name, String purpose) {
		if (status != ProjectStatus.ACTIVE) {
			throw new IllegalStateException(
					"Un proyecto retirado del servicio no se modifica. Reincorporelo antes");
		}
		if (name != null && !name.isBlank()) {
			this.name = name.trim();
		}
		if (purpose != null) {
			this.purpose = purpose.trim();
		}
	}

	/**
	 * Retira el proyecto del servicio.
	 *
	 * <p>Conforme a ADM-01, el contenido permanece: un proyecto retirado deja de
	 * admitir trabajo y sigue siendo consultable. Eliminarlo borraria el rastro de
	 * cuanto se decidio en el.</p>
	 */
	public void retirar() {
		this.status = ProjectStatus.DECOMMISSIONED;
	}

	/** Devuelve al servicio un proyecto retirado. */
	public void reincorporar() {
		this.status = ProjectStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public String getName() {
		return name;
	}

	public String getPurpose() {
		return purpose;
	}

	public ProjectStatus getStatus() {
		return status;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Project p && Objects.equals(id, p.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
