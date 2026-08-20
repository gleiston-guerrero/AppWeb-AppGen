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
		String actor,

		/**
		 * Caso de uso expandido aceptado que realiza este requisito, si lo hay.
		 *
		 * <p>Es lo que el equipo decidio y acepto: quien inicia, que pasos, y --- lo
		 * que ningun requisito trae --- que ocurre cuando algo falla. De ahi salen
		 * los caminos negativos sin que nadie tenga que inventarlos.</p>
		 *
		 * <p>Nulo cuando no existe: entonces se genera del requisito, que es lo unico
		 * que hay, y lo que falte quedara como hueco.</p>
		 */
		String useCase) {

	/** Alta sin caso de uso, para los sitios que aun no lo tienen. */
	public RequirementInput(String readableId, String sourceId, String kind, String name,
			String statement, String verification, String actor) {

		this(readableId, sourceId, kind, name, statement, verification, actor, null);
	}

	/** Si hay un caso de uso aceptado del que derivar. */
	public boolean tieneCasoDeUso() {
		return useCase != null && !useCase.isBlank();
	}

	/** Identificador que reconoce la gente del proyecto. */
	public String etiqueta() {
		return sourceId == null || sourceId.isBlank() ? readableId : sourceId;
	}

	public boolean tieneCriterio() {
		return verification != null && !verification.isBlank();
	}
}
