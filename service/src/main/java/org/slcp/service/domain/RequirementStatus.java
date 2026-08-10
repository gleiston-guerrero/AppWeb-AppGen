package org.slcp.service.domain;

/**
 * Estados de un requisito.
 *
 * <p>El cierre no figura aqui: RQM-14 lo calcula a partir de la aceptacion de
 * los entregables enlazados, y lo que se calcula no se almacena.</p>
 */
public enum RequirementStatus {

	/** Redactado o importado, sin revisar. */
	DRAFT,

	/** Superada la revision previa de RQM-05. */
	REVIEWED,

	/** Aprobado por el propietario del producto. Habilita el trabajo. */
	APPROVED,

	/** Rechazado con motivo. */
	REJECTED,

	/** Sustituido por otro requisito (RQM-16). */
	SUPERSEDED,

	/** Anulado: deja de exigirse, y permanece (RQM-18). */
	ANNULLED;

	public boolean puedeTransitarA(RequirementStatus destino) {
		return switch (this) {
			case DRAFT -> destino == REVIEWED || destino == REJECTED || destino == ANNULLED;
			case REVIEWED -> destino == APPROVED || destino == REJECTED || destino == DRAFT;
			case APPROVED -> destino == DRAFT || destino == SUPERSEDED || destino == ANNULLED;
			case REJECTED -> destino == DRAFT;
			case SUPERSEDED, ANNULLED -> false;
		};
	}

	/** Indica si el estado admite modificar el texto sin volver atras. */
	public boolean admiteEdicion() {
		return this == DRAFT || this == REVIEWED;
	}
}
