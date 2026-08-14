package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Componente: parte de un entregable con identidad propia.
 *
 * <p>Nivel 3 de la descomposicion. Su avance sale de sus tareas, ponderado por
 * el esfuerzo previsto de cada una, y no se guarda en ninguna parte.</p>
 */
@Entity
@Table(name = "components")
public class Component {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "deliverable_id", nullable = false, updatable = false)
	private UUID deliverableId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Column(name = "description")
	private String description;

	/** Marca de que llego a tener trabajo terminado. Impide su borrado. */
	@Column(name = "ever_decided", nullable = false)
	private boolean everDecided;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Component() {
	}

	public static Component crear(UUID projectId, UUID deliverableId, String readableId,
			String name, String description, UUID createdBy, Instant momento) {

		Component c = new Component();
		c.id = UUID.randomUUID();
		c.readableId = readableId;
		c.deliverableId = deliverableId;
		c.projectId = projectId;
		c.name = TextNormalizer.nombre(name);
		c.description = TextNormalizer.enunciado(description);
		c.createdBy = createdBy;
		c.createdAt = momento;
		c.updatedAt = momento;
		return c;
	}

	public void editar(String name, String description, Instant momento) {
		if (name != null && !name.isBlank()) {
			this.name = TextNormalizer.nombre(name);
		}
		this.description = TextNormalizer.enunciado(description);
		this.updatedAt = momento;
	}

	/** Deja constancia de que tuvo trabajo terminado, lo que impide eliminarlo. */
	public void marcarDecidido() {
		this.everDecided = true;
	}

	public boolean puedeEliminarse() {
		return !everDecided;
	}

	public String getDescription() {
		return description;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public UUID getDeliverableId() {
		return deliverableId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Component o && Objects.equals(id, o.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
