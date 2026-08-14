package org.slcp.service.generation;

/**
 * Lo que un generador necesita saber de un requisito.
 *
 * <p>Se pasa asi, y no la entidad, para que los generadores no dependan de como
 * se almacenan los requisitos: uno de ellos enviara este texto a un servicio
 * externo, y conviene que sea evidente que sale y que no.</p>
 */
public record RequirementInput(
		String readableId,
		String sourceId,
		String kind,
		String name,
		String statement,
		String verification,
		String actor) {

	/** Identificador que reconoce la gente del proyecto. */
	public String etiqueta() {
		return sourceId == null || sourceId.isBlank() ? readableId : sourceId;
	}

	public boolean tieneCriterio() {
		return verification != null && !verification.isBlank();
	}
}
