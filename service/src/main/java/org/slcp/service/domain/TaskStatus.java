package org.slcp.service.domain;

/**
 * Estados de una tarea.
 *
 * <p>El avance no figura aqui: se calcula de sus actividades (PRG-06). El estado
 * dice en que situacion esta; el avance, cuanto se lleva hecho.</p>
 */
public enum TaskStatus {

	PENDING("Pendiente"),
	IN_PROGRESS("En curso"),
	DONE("Terminada"),
	/** Detenida por algo ajeno a quien la ejecuta. */
	BLOCKED("Bloqueada");

	private final String etiqueta;

	TaskStatus(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	public boolean puedeTransitarA(TaskStatus destino) {
		return switch (this) {
			case PENDING -> destino == IN_PROGRESS || destino == BLOCKED || destino == DONE;
			case IN_PROGRESS -> destino == DONE || destino == BLOCKED || destino == PENDING;
			case BLOCKED -> destino == IN_PROGRESS || destino == PENDING;
			// Reabrir una tarea terminada es legitimo: aparecio algo que faltaba.
			case DONE -> destino == IN_PROGRESS;
		};
	}
}
