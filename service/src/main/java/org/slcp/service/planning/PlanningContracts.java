package org.slcp.service.planning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos de la descomposicion del trabajo. */
public final class PlanningContracts {

	private PlanningContracts() {
	}

	// --- Peticiones -------------------------------------------------

	public record ComponentRequest(
			@NotBlank(message = "El nombre del componente es obligatorio")
			@Size(max = 300) String name,
			@Size(max = 4000) String description) {
	}

	public record TaskRequest(
			@NotBlank(message = "El nombre de la tarea es obligatorio")
			@Size(max = 300) String name,
			@Size(max = 4000) String description,

			/**
			 * Obligatorio: sin el, el avance del componente se promediaria sin peso y
			 * terminar lo trivial dejando lo dificil daria un avance enganoso.
			 */
			@Positive(message = "El esfuerzo previsto es obligatorio y mayor que cero")
			Integer plannedEffort,

			/** Identificador de quien la ejecuta. Una sola persona. */
			String assignee) {
	}

	public record ActivityRequest(
			@NotBlank(message = "El nombre de la actividad es obligatorio")
			@Size(max = 300) String name,
			@Positive(message = "El esfuerzo de la actividad ha de ser mayor que cero")
			Integer plannedEffort) {
	}

	public record TimeEntryRequest(
			@Positive(message = "Las horas dedicadas han de ser mayores que cero")
			BigDecimal hours,
			LocalDate workedOn,
			@Size(max = 2000) String note) {
	}

	public record ResourceRequest(
			@NotBlank(message = "El nombre del recurso es obligatorio")
			@Size(max = 300) String name,
			String kind,
			@Size(max = 40) String unit,
			BigDecimal quantity,
			@Size(max = 2000) String notes) {
	}

	public record ResourceAssignmentRequest(String resource, BigDecimal quantity) {
	}

	// --- Vistas -----------------------------------------------------

	/** Horas dedicadas a una actividad. */
	public record TimeEntryView(
			/** Necesario para poder retirarlo: sin el, la interfaz no sabria cual. */
			String id,
			String person, BigDecimal hours, LocalDate workedOn, String note) {
	}

	public record ActivityView(
			String readableId,
			String name,
			int plannedEffort,
			boolean done,
			Instant doneAt,
			BigDecimal spentHours,
			List<TimeEntryView> entries) {
	}

	/** Recurso material asignado a una tarea. */
	public record AssignedResourceView(
			String readableId, String name, String kindLabel, String unit, BigDecimal quantity) {
	}

	public record TaskView(
			String readableId,
			String name,
			String description,
			int plannedEffort,
			String assignee,
			String assigneeName,
			String status,
			String statusLabel,
			String doneBy,
			/** Calculado de sus actividades, ponderado por su esfuerzo. */
			BigDecimal progress,
			BigDecimal spentHours,
			List<ActivityView> activities,
			List<AssignedResourceView> resources,
			Instant updatedAt) {
	}

	public record ComponentView(
			String readableId,
			String name,
			String description,
			int effort,
			BigDecimal progress,
			BigDecimal spentHours,
			boolean deletable,
			List<TaskView> tasks,
			Instant updatedAt) {
	}

	/** Un entregable con su descomposicion y su avance. */
	public record DeliverableBreakdownView(
			String deliverableId,
			String deliverableName,
			String deliverableStatus,
			int effort,
			BigDecimal progress,
			BigDecimal spentHours,
			List<ComponentView> components) {
	}

	public record ResourceView(
			String readableId,
			String name,
			String kind,
			String kindLabel,
			String unit,
			BigDecimal quantity,
			String notes,
			/** Cuantas tareas lo emplean. Impide borrar lo que esta en uso. */
			long assignments) {
	}

	/** Carga de trabajo de una persona del equipo. */
	public record WorkloadView(
			String username,
			String fullName,
			long tasks,
			int effort,
			BigDecimal spentHours,
			BigDecimal progress) {
	}

	/** Avance del proyecto entero, con su desglose. */
	public record PlanView(
			int effort,
			BigDecimal progress,
			BigDecimal spentHours,
			List<DeliverableBreakdownView> deliverables,
			List<ResourceView> resources,
			List<WorkloadView> workload) {
	}
}
