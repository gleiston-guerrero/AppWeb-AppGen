package org.slcp.service.domain;

/**
 * Estados de un entregable.
 *
 * <p>El avance no figura aqui: se calcula de sus componentes, conforme a
 * PRG-06. El estado dice en que fase esta; el avance, cuanto se lleva hecho, y
 * confundirlos llevaria a declarar a mano lo que debe derivarse.</p>
 */
public enum DeliverableStatus {

	/** Definido y sin trabajo empezado. */
	PLANNED,

	/** Con trabajo en curso. */
	IN_PROGRESS,

	/** Terminado por el equipo y a la espera de aceptacion. */
	DELIVERED,

	/** Aceptado por el propietario del producto. Cierra sus requisitos. */
	ACCEPTED,

	/** Devuelto por el propietario, con motivo. */
	REJECTED;

	public boolean puedeTransitarA(DeliverableStatus destino) {
		return switch (this) {
			case PLANNED -> destino == IN_PROGRESS || destino == DELIVERED;
			case IN_PROGRESS -> destino == DELIVERED || destino == PLANNED;
			case DELIVERED -> destino == ACCEPTED || destino == REJECTED;
			case REJECTED -> destino == IN_PROGRESS || destino == DELIVERED;
			case ACCEPTED -> false;
		};
	}

	/** Indica si el entregable admite que se modifiquen sus datos. */
	public boolean admiteEdicion() {
		return this != ACCEPTED;
	}
}
