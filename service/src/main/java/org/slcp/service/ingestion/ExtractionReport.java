package org.slcp.service.ingestion;

import java.util.List;
import java.util.Map;

/**
 * Resultado de leer un documento.
 *
 * <p>Incluye lo extraido y lo que falta. Un informe que solo dijera lo
 * encontrado dejaria a quien importa sin saber que el documento tiene huecos,
 * que es justamente lo que necesita saber antes de trabajar sobre el.</p>
 *
 * @param profileId       perfil empleado
 * @param requirements    requisitos extraidos, en orden de aparicion
 * @param duplicateIds    identificadores que aparecen mas de una vez
 * @param missingByField  cuantos requisitos carecen de cada campo esperado
 * @param unknownLabels   etiquetas del documento que el perfil no reconoce
 */
public record ExtractionReport(
		String profileId,
		List<ParsedRequirement> requirements,
		List<String> duplicateIds,
		Map<String, Integer> missingByField,
		List<String> unknownLabels) {

	public int total() {
		return requirements.size();
	}

	public long completos() {
		return requirements.stream().filter(ParsedRequirement::completo).count();
	}

	public long incompletos() {
		return total() - completos();
	}
}
