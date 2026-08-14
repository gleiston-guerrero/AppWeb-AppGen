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

/**
 * Tarea: unidad de trabajo asignable a una persona.
 *
 * <p>Cuelga siempre de un componente (WBS-06). Lleva esfuerzo previsto porque
 * sin el, el avance del componente tendria que promediarse sin peso, y terminar
 * cinco tareas triviales dejando la dificil daria un ochenta y tres por
 * ciento.</p>
 *
 * <p>Se asigna a una sola persona: con varias, la carga por persona deja de
 * poder calcularse y nadie responde de la tarea. Si hace falta mas gente, se
 * divide.</p>
 */
@Entity
@Table(name = "tasks")
public class Task {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "component_id", nullable = false, updatable = false)
	private UUID componentId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "planned_effort", nullable = false)
	private int plannedEffort;

	@Column(name = "assignee_id")
	private UUID assigneeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private TaskStatus status;

	@Column(name = "done_at")
	private Instant doneAt;

	@Column(name = "done_by")
	private UUID doneBy;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Task() {
	}

	public static Task crear(UUID projectId, UUID componentId, String readableId, String name,
			String description, int plannedEffort, UUID assigneeId, UUID createdBy, Instant momento) {

		if (plannedEffort <= 0) {
			throw new IllegalArgumentException(
					"El esfuerzo previsto es obligatorio y ha de ser mayor que cero: sin el, el "
							+ "avance del componente se promediaria sin peso");
		}

		Task t = new Task();
		t.id = UUID.randomUUID();
		t.readableId = readableId;
		t.componentId = componentId;
		t.projectId = projectId;
		t.name = TextNormalizer.nombre(name);
		t.description = TextNormalizer.enunciado(description);
		t.plannedEffort = plannedEffort;
		t.assigneeId = assigneeId;
		t.status = TaskStatus.PENDING;
		t.createdBy = createdBy;
		t.createdAt = momento;
		t.updatedAt = momento;
		return t;
	}

	public void editar(String name, String description, Integer plannedEffort, UUID assigneeId,
			Instant momento) {

		if (name != null && !name.isBlank()) {
			this.name = TextNormalizer.nombre(name);
		}
		this.description = TextNormalizer.enunciado(description);

		if (plannedEffort != null) {
			if (plannedEffort <= 0) {
				throw new IllegalArgumentException("El esfuerzo previsto ha de ser mayor que cero");
			}
			this.plannedEffort = plannedEffort;
		}
		this.assigneeId = assigneeId;
		this.updatedAt = momento;
	}

	/**
	 * Cambia el estado.
	 *
	 * <p>PRG-08: la da por terminada quien la ejecuta, y consta quien y cuando.
	 * El nivel intermedio no necesita otra firma; la que cuenta es la aceptacion
	 * del entregable, que corresponde al propietario del producto.</p>
	 */
	public void transitarA(TaskStatus destino, UUID autor, Instant momento) {
		if (!status.puedeTransitarA(destino)) {
			throw new IllegalStateException("Transicion no admitida de "
					+ status.getEtiqueta() + " a " + destino.getEtiqueta());
		}

		if (destino == TaskStatus.DONE) {
			this.doneBy = autor;
			this.doneAt = momento;
		} else {
			// Al reabrirse deja de constar terminada: mantener el dato diria que
			// alguien la dio por hecha y sigue sin estarlo.
			this.doneBy = null;
			this.doneAt = null;
		}

		this.status = destino;
		this.updatedAt = momento;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public UUID getComponentId() {
		return componentId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public int getPlannedEffort() {
		return plannedEffort;
	}

	public UUID getAssigneeId() {
		return assigneeId;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public UUID getDoneBy() {
		return doneBy;
	}

	public Instant getDoneAt() {
		return doneAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Task t && Objects.equals(id, t.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
