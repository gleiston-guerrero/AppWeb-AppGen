package org.slcp.service.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Lee un documento de requisitos conforme a un perfil.
 *
 * <p>Extrae y no interpreta. Lo que el documento dice queda tal cual, y lo que
 * no dice se reporta como ausente en lugar de deducirse: un importador que
 * completa huecos produce una especificacion que nadie escribio.</p>
 */
public final class RequirementExtractor implements RequirementSource {

	private final ImportProfile perfil;

	public RequirementExtractor(ImportProfile perfil) {
		this.perfil = perfil;
	}

	/** Lee el documento completo y devuelve lo extraido junto con sus carencias. */
	@Override
	public ExtractionReport extraer(Reader documento) throws IOException {
		List<ParsedRequirement> requisitos = new ArrayList<>();
		Set<String> vistos = new HashSet<>();
		Set<String> duplicados = new LinkedHashSet<>();
		Set<String> etiquetasDesconocidas = new LinkedHashSet<>();
		Map<String, Integer> ausenciasPorCampo = new LinkedHashMap<>();

		try (BufferedReader lector = new BufferedReader(documento)) {
			String linea;
			int numero = 0;
			boolean dentro = false;
			int lineaInicio = 0;
			String identificador = "";
			Map<String, String> campos = new LinkedHashMap<>();
			List<String> desconocidas = new ArrayList<>();

			while ((linea = lector.readLine()) != null) {
				numero++;
				String limpia = linea.trim();

				boolean empieza = limpia.startsWith(perfil.getBlockBegin());

				// Un bloque sin marca de cierre termina donde empieza el siguiente, o
				// en la primera linea vacia. Sin esto, el lector solo servirla para
				// formatos que delimitan el fin de forma expresa, que son los menos.
				boolean termina = dentro && (
						(perfil.cierraEnSiguiente() && empieza)
						|| (perfil.cierraEnLineaVacia() && limpia.isEmpty())
						|| (!perfil.cierraEnSiguiente() && !perfil.cierraEnLineaVacia()
								&& limpia.startsWith(perfil.getBlockEnd())));

				if (termina) {
					dentro = false;

					List<String> ausentes = ausentesDe(campos);
					for (String ausente : ausentes) {
						ausenciasPorCampo.merge(ausente, 1, Integer::sum);
					}

					String idFinal = campos.getOrDefault("id", identificador);
					if (!vistos.add(idFinal)) {
						duplicados.add(idFinal);
					}
					etiquetasDesconocidas.addAll(desconocidas);

					requisitos.add(new ParsedRequirement(idFinal, campos, desconocidas,
							ausentes, lineaInicio));

					// Si el cierre lo provoco el comienzo del siguiente, hay que abrirlo
					// aqui: la linea es a la vez fin de uno y principio de otro.
					if (!(perfil.cierraEnSiguiente() && empieza)) {
						continue;
					}
				}

				if (!dentro && empieza) {
					dentro = true;
					lineaInicio = numero;
					campos = new LinkedHashMap<>();
					desconocidas = new ArrayList<>();

					Matcher m = perfil.getIdPattern().matcher(limpia);
					identificador = m.find() ? m.group(1).trim() : "";

					// El identificador de la cabecera es un campo como cualquier otro:
					// si no se anota, la comprobacion de completitud lo dara por
					// ausente aunque este a la vista en la propia linea.
					if (!identificador.isEmpty()) {
						campos.put("id", identificador);
					}

					if (perfil.getNamePattern() != null) {
						Matcher n = perfil.getNamePattern().matcher(limpia);
						if (n.find()) {
							campos.put("name", n.group(1).trim());
						}
					}
					continue;
				}

				if (dentro) {
					leerFila(limpia, campos, desconocidas);
				}
			}

			// Un formato sin marca de cierre deja el ultimo bloque abierto al acabar
			// el documento. Descartarlo perderia siempre un requisito, y justo el
			// ultimo, que es el que menos se echa en falta al revisar.
			if (dentro) {
				List<String> ausentes = ausentesDe(campos);
				for (String ausente : ausentes) {
					ausenciasPorCampo.merge(ausente, 1, Integer::sum);
				}
				String idFinal = campos.getOrDefault("id", identificador);
				if (!vistos.add(idFinal)) {
					duplicados.add(idFinal);
				}
				etiquetasDesconocidas.addAll(desconocidas);
				requisitos.add(new ParsedRequirement(idFinal, campos, desconocidas,
						ausentes, lineaInicio));
			}
		}

		return new ExtractionReport(perfil.getId(), requisitos,
				new ArrayList<>(duplicados), ausenciasPorCampo,
				new ArrayList<>(etiquetasDesconocidas));
	}

	/**
	 * Interpreta una fila del bloque como etiqueta y valor.
	 *
	 * <p>Una fila sin separador no es un error: los documentos reales incluyen
	 * lineas de formato, comentarios y continuaciones. Se ignoran en lugar de
	 * hacer fracasar la lectura entera.</p>
	 */
	private void leerFila(String linea, Map<String, String> campos, List<String> desconocidas) {
		if (linea.isEmpty() || linea.startsWith("%")) {
			return;
		}

		int separador = linea.indexOf(perfil.getRowSeparator());
		if (separador < 0) {
			return;
		}

		String etiqueta = linea.substring(0, separador).trim();
		String valor = linea.substring(separador + perfil.getRowSeparator().length()).trim();

		if (!perfil.getRowTerminator().isEmpty() && valor.endsWith(perfil.getRowTerminator())) {
			valor = valor.substring(0, valor.length() - perfil.getRowTerminator().length()).trim();
		}

		// El marcado que rodea al valor no forma parte del dato: en Markdown, el
		// cierre en negrita queda del lado del valor al partir por el separador.
		valor = valor.replaceAll("^[\\s*_]+", "").trim();
		if (etiqueta.isEmpty()) {
			return;
		}

		String campo = perfil.campoDe(etiqueta);
		if (campo.isEmpty()) {
			desconocidas.add(etiqueta);
			return;
		}

		// Un campo repetido con valor distinto se concatena en lugar de sustituirse:
		// perder el primero seria perder informacion del origen. Repetido con el
		// mismo valor no aporta nada y se descarta, que es lo que ocurre cuando el
		// identificador figura a la vez en la cabecera y en su propia fila.
		campos.merge(campo, valor,
				(anterior, nuevo) -> anterior.equals(nuevo) ? anterior : anterior + " " + nuevo);
	}

	private List<String> ausentesDe(Map<String, String> campos) {
		List<String> ausentes = new ArrayList<>();
		for (String esperado : perfil.getExpected()) {
			String valor = campos.get(esperado);
			if (valor == null || valor.isBlank()) {
				ausentes.add(esperado);
			}
		}
		return ausentes;
	}
}
