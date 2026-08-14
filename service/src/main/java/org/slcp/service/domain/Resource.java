package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Recurso material del proyecto.
 *
 * <p>Equipos, licencias, instalaciones, consumibles y servicios contratados. Las
 * personas no se catalogan aqui: ya son miembros del equipo, y tenerlas en dos
 * sitios acabaria con dos listas que discrepan.</p>
 */
@Entity
@Table(name = "resources")
public class Resource {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20)
	private ResourceKind kind;

	@Column(name = "unit", length = 40)
	private String unit;

	@Column(name = "quantity")
	private BigDecimal quantity;

	@Column(name = "notes")
	private String notes;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Resource() {
	}

	public static Resource crear(UUID projectId, String readableId, String name, ResourceKind kind,
			String unit, BigDecimal quantity, String notes, UUID createdBy, Instant momento) {

		Resource r = new Resource();
		r.id = UUID.randomUUID();
		r.readableId = readableId;
		r.projectId = projectId;
		r.name = TextNormalizer.nombre(name);
		r.kind = kind == null ? ResourceKind.OTHER : kind;
		r.unit = unit == null || unit.isBlank() ? null : unit.trim();
		r.quantity = quantity;
		r.notes = TextNormalizer.enunciado(notes);
		r.createdBy = createdBy;
		r.createdAt = momento;
		r.updatedAt = momento;
		return r;
	}

	public void editar(String name, ResourceKind kind, String unit, BigDecimal quantity,
			String notes, Instant momento) {

		if (name != null && !name.isBlank()) {
			this.name = TextNormalizer.nombre(name);
		}
		if (kind != null) {
			this.kind = kind;
		}
		this.unit = unit == null || unit.isBlank() ? null : unit.trim();
		this.quantity = quantity;
		this.notes = TextNormalizer.enunciado(notes);
		this.updatedAt = momento;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public ResourceKind getKind() {
		return kind;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public String getNotes() {
		return notes;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Resource r && Objects.equals(id, r.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
