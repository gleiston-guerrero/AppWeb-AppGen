package org.slcp.service.ingestion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Requisito tal como aparece en el documento de origen, sin interpretar.
 *
 * <p>Se conserva lo extraido literalmente y ademas lo que falta. Rellenar un
 * hueco seria inventar, y TRC-13 distingue entre lo que el documento dice y lo
 * que alguien supone que quiso decir.</p>
 *
 * @param sourceId     identificador tal como figura en el documento
 * @param fields       campos canonicos con su valor literal
 * @param unknownLabels etiquetas del documento que el perfil no reconoce
 * @param missing      campos esperados que el documento no trae
 * @param sourceLine   linea del documento donde empieza, para poder volver a ella
 */
public record ParsedRequirement(
		String sourceId,
		Map<String, String> fields,
		List<String> unknownLabels,
		List<String> missing,
		int sourceLine) {

	public ParsedRequirement {
		fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
		unknownLabels = List.copyOf(unknownLabels);
		missing = List.copyOf(missing);
	}

	public String get(String campo) {
		return fields.getOrDefault(campo, "");
	}

	public boolean tiene(String campo) {
		return !get(campo).isBlank();
	}

	/** Un requisito esta completo cuando trae todos los campos esperados. */
	public boolean completo() {
		return missing.isEmpty();
	}
}
