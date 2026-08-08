package org.slcp.service.domain;

/**
 * Estados de una cuenta de usuario.
 *
 * <p>Realiza la maquina de estados de FUN-15 y FUN-16: quien se registra queda
 * pendiente de aprobacion y no obtiene capacidad alguna hasta que el
 * administrador se pronuncia. La baja de ADM-01 es un estado mas y nunca una
 * eliminacion.</p>
 */
public enum UserStatus {

	/** Solicitud registrada, sin capacidad alguna hasta ser aprobada. */
	PENDING_APPROVAL,

	/** Cuenta aprobada y operativa. */
	ACTIVE,

	/** Solicitud rechazada por el administrador, con motivo registrado. */
	REJECTED,

	/** Retirada del servicio conforme a ADM-01. El contenido permanece. */
	DECOMMISSIONED;

	/** Indica si desde este estado se admite la transicion indicada. */
	public boolean puedeTransitarA(UserStatus destino) {
		return switch (this) {
			case PENDING_APPROVAL -> destino == ACTIVE || destino == REJECTED;
			case ACTIVE -> destino == DECOMMISSIONED;
			case DECOMMISSIONED -> destino == ACTIVE;
			case REJECTED -> false;
		};
	}
}
