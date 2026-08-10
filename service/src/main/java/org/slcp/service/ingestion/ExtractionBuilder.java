package org.slcp.service.ingestion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Acumula lo extraido y calcula sus carencias.
 *
 * <p>Se comparte entre todos los lectores. Lo que cambia de un formato a otro es
 * como se obtienen los campos; lo que se hace con ellos --- comprobar lo que
 * falta, detectar identificadores repetidos, recoger etiquetas no reconocidas ---
 * es lo mismo, y repetirlo en cada lector garantizaria que acabaran
 * discrepando.</p>
 */
final class ExtractionBuilder {

	private final ImportProfile perfil;
	private final List<ParsedRequirement> requisitos = new ArrayList<>();
	private final Set<String> vistos = new HashSet<>();
	private final Set<String> duplicados = new LinkedHashSet<>();
	private final Set<String> etiquetasDesconocidas = new LinkedHashSet<>();
	private final Map<String, Integer> ausenciasPorCampo = new LinkedHashMap<>();

	ExtractionBuilder(ImportProfile perfil) {
		this.perfil = perfil;
	}

	/**
	 * Anota un requisito ya resuelto en campos canonicos.
	 *
	 * @param campos       campos canonicos con su valor
	 * @param desconocidas etiquetas que el perfil no reconoce
	 * @param posicion     linea, fila o indice de origen
	 */
	void anadir(Map<String, String> campos, List<String> desconocidas, int posicion) {
		List<String> ausentes = new ArrayList<>();
		for (String esperado : perfil.getExpected()) {
			String valor = campos.get(esperado);
			if (valor == null || valor.isBlank()) {
				ausentes.add(esperado);
				ausenciasPorCampo.merge(esperado, 1, Integer::sum);
			}
		}

		String id = campos.getOrDefault("id", "");
		if (!vistos.add(id)) {
			duplicados.add(id);
		}
		etiquetasDesconocidas.addAll(desconocidas);

		requisitos.add(new ParsedRequirement(id, campos, desconocidas, ausentes, posicion));
	}

	ExtractionReport construir() {
		return new ExtractionReport(perfil.getId(), requisitos,
				new ArrayList<>(duplicados), ausenciasPorCampo,
				new ArrayList<>(etiquetasDesconocidas));
	}
}
