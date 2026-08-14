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
 * Actividad: ocupacion concreta dentro de una tarea.
 *
 * <p>Ultimo nivel de la descomposicion y el unico que registra hechos. De aqui
 * sale el avance de la tarea, y de la tarea el de todo lo demas.</p>
 *
 * <p>Lleva su propio esfuerzo previsto para poder ponderar dentro de la tarea:
 * estudiar como resolver algo y escribir la prueba no valen lo mismo, y contar
 * actividades sin peso repetiria dentro de la tarea el error que PRG-07 evita
 * fuera.</p>
 */
@Entity
@Table(name = "activities")
public class Activity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Column(name = "planned_effort", nullable = false)
	private int plannedEffort;

	@Column(name = "done", nullable = false)
	private boolean done;

	@Column(name = "done_at")
	private Instant doneAt;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Activity() {
	}

	public static Activity crear(UUID projectId, UUID taskId, String readableId, String name,
			int plannedEffort, UUID createdBy, Instant momento) {

		if (plannedEffort <= 0) {
			throw new IllegalArgumentException("El esfuerzo de la actividad ha de ser mayor que cero");
		}

		Activity a = new Activity();
		a.id = UUID.randomUUID();
		a.readableId = readableId;
		a.taskId = taskId;
		a.projectId = projectId;
		a.name = TextNormalizer.nombre(name);
		a.plannedEffort = plannedEffort;
		a.createdBy = createdBy;
		a.createdAt = momento;
		a.updatedAt = momento;
		return a;
	}

	public void editar(String name, Integer plannedEffort, Instant momento) {
		if (name != null && !name.isBlank()) {
			this.name = TextNormalizer.nombre(name);
		}
		if (plannedEffort != null) {
			if (plannedEffort <= 0) {
				throw new IllegalArgumentException("El esfuerzo ha de ser mayor que cero");
			}
			this.plannedEffort = plannedEffort;
		}
		this.updatedAt = momento;
	}

	/** Da la actividad por hecha, o la devuelve a pendiente. */
	public void marcar(boolean hecha, Instant momento) {
		this.done = hecha;
		this.doneAt = hecha ? momento : null;
		this.updatedAt = momento;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public int getPlannedEffort() {
		return plannedEffort;
	}

	public boolean isDone() {
		return done;
	}

	public Instant getDoneAt() {
		return doneAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Activity a && Objects.equals(id, a.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
