package org.slcp.service.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Comprueba la redaccion de un requisito conforme a ISO/IEC/IEEE 29148.
 *
 * <p>Cada defecto se remite a la caracteristica de la norma que incumple,
 * conforme a RQM-11: una regla que no pueda justificarse contra la norma seria
 * una mania y no un criterio.</p>
 *
 * <p>Los vocabularios se declaran fuera del codigo (RQM-12). Lo que en un
 * dominio es un termino vago no lo es en otro: <em>critico</em> es impreciso en
 * una aplicacion de gestion y tiene significado exacto en una clinica.</p>
 *
 * <p>El validador senala y no corrige. Lo que puede proponerse, y con que
 * limite, se rige por SLCP-ADR-0007.</p>
 */
public final class RequirementLinter {

	/** Caracteristica de la norma que protege cada regla. */
	public enum Caracteristica {
		NO_AMBIGUO("No ambiguo"),
		SINGULAR("Singular"),
		VERIFICABLE("Verificable"),
		CONFORME("Conforme"),
		COMPLETO("Completo");

		private final String etiqueta;

		Caracteristica(String etiqueta) {
			this.etiqueta = etiqueta;
		}

		public String getEtiqueta() {
			return etiqueta;
		}
	}

	/** Gravedad del hallazgo. */
	public enum Gravedad {
		/** Incumple la norma. */
		DEFECTO,
		/** Puede incumplirla; exige mirada humana. */
		SOSPECHA
	}

	/**
	 * Un hallazgo sobre un requisito.
	 *
	 * @param regla          identificador de la regla
	 * @param caracteristica lo que la norma exige y este enunciado no cumple
	 * @param gravedad       si es cierto o solo sospechoso
	 * @param evidencia      el fragmento del enunciado que lo motiva
	 * @param explicacion    que hay que corregir, en terminos de la norma
	 */
	public record Hallazgo(String regla, Caracteristica caracteristica, Gravedad gravedad,
			String evidencia, String explicacion) {
	}

	private final Map<String, List<String>> vocabularios;
	private final int maxPalabras;

	private RequirementLinter(Map<String, List<String>> vocabularios, int maxPalabras) {
		this.vocabularios = vocabularios;
		this.maxPalabras = maxPalabras;
	}

	/** Carga las reglas desde su declaracion. */
	public static RequirementLinter cargar(Reader origen) throws IOException {
		Map<String, List<String>> vocabularios = new LinkedHashMap<>();
		int maxPalabras = 50;

		try (BufferedReader lector = new BufferedReader(origen)) {
			StringBuilder acumulado = new StringBuilder();
			String linea;

			while ((linea = lector.readLine()) != null) {
				String limpia = linea.trim();
				if (limpia.isEmpty() || limpia.startsWith("#")) {
					continue;
				}
				// Una linea terminada en barra continua en la siguiente.
				if (limpia.endsWith("\\")) {
					acumulado.append(limpia, 0, limpia.length() - 1).append(' ');
					continue;
				}
				acumulado.append(limpia);
				String completa = acumulado.toString();
				acumulado.setLength(0);

				int igual = completa.indexOf('=');
				if (igual < 0) {
					continue;
				}
				String clave = completa.substring(0, igual).trim();
				String valor = completa.substring(igual + 1).trim();

				if ("max.words".equals(clave)) {
					maxPalabras = Integer.parseInt(valor);
					continue;
				}
				List<String> terminos = new ArrayList<>();
				for (String t : valor.split(",")) {
					if (!t.isBlank()) {
						terminos.add(normalizar(t));
					}
				}
				vocabularios.put(clave, terminos);
			}
		}
		return new RequirementLinter(vocabularios, maxPalabras);
	}

	/** Examina el enunciado de un requisito. */
	public List<Hallazgo> examinar(String enunciado) {
		List<Hallazgo> hallazgos = new ArrayList<>();
		if (enunciado == null || enunciado.isBlank()) {
			hallazgos.add(new Hallazgo("enunciado-ausente", Caracteristica.COMPLETO,
					Gravedad.DEFECTO, "",
					"El requisito no tiene enunciado; sin el no puede entenderse ni verificarse"));
			return hallazgos;
		}

		String texto = normalizar(enunciado);

		// --- Conforme: el verbo debe obligar -----------------------------
		if (!contieneAlguno(texto, "verb.binding")) {
			String preferencia = primeroPresente(texto, "verb.preference");
			String permiso = primeroPresente(texto, "verb.permission");

			if (!preferencia.isEmpty()) {
				hallazgos.add(new Hallazgo("verbo-no-vinculante", Caracteristica.CONFORME,
						Gravedad.DEFECTO, preferencia,
						"Expresa una preferencia y no una obligacion. Una preferencia no es un "
								+ "requisito: o se enuncia con verbo vinculante o se retira del conjunto"));
			} else if (!permiso.isEmpty()) {
				hallazgos.add(new Hallazgo("verbo-permisivo", Caracteristica.CONFORME,
						Gravedad.DEFECTO, permiso,
						"Expresa una concesion y no una obligacion. Lo que se permite no obliga a nada"));
			} else {
				hallazgos.add(new Hallazgo("sin-verbo-vinculante", Caracteristica.CONFORME,
						Gravedad.SOSPECHA, "",
						"No se reconoce un verbo que obligue. La norma reserva la forma vinculante "
								+ "para los requisitos y otras formas para el texto descriptivo"));
			}
		}

		// --- No ambiguo: el sujeto debe constar --------------------------
		if (!contieneAlguno(texto, "subject")) {
			hallazgos.add(new Hallazgo("sujeto-ausente", Caracteristica.NO_AMBIGUO,
					Gravedad.SOSPECHA, "",
					"No se reconoce el sujeto. La norma exige decir quien actua, y ese sujeto debe "
							+ "ser el sistema y no la persona que lo usa"));
		}

		// --- No ambiguo: voz pasiva --------------------------------------
		String pasiva = primeroPresente(texto, "forbidden.passive");
		if (!pasiva.isEmpty()) {
			hallazgos.add(new Hallazgo("voz-pasiva", Caracteristica.NO_AMBIGUO,
					Gravedad.DEFECTO, pasiva,
					"Voz pasiva: no dice quien realiza la accion. La norma exige voz activa"));
		}

		// --- Conforme: capacidad en lugar de accion ----------------------
		String capacidad = primeroPresente(texto, "forbidden.capability");
		if (!capacidad.isEmpty()) {
			hallazgos.add(new Hallazgo("capacidad-en-lugar-de-accion", Caracteristica.CONFORME,
					Gravedad.DEFECTO, capacidad,
					"Enuncia una capacidad y no una accion. Poder hacer algo no obliga a hacerlo, "
							+ "y lo que se verifica es la accion"));
		}

		// --- Conforme: enunciado negativo --------------------------------
		String negativo = primeroPresente(texto, "forbidden.negative");
		if (!negativo.isEmpty()) {
			hallazgos.add(new Hallazgo("enunciado-negativo", Caracteristica.CONFORME,
					Gravedad.SOSPECHA, negativo,
					"Enunciado en negativo. Demostrar que algo nunca ocurre exige recorrer todos los "
							+ "casos; enunciar en positivo lo que si debe ocurrir suele ser verificable"));
		}

		// --- Verificable: terminos sin magnitud --------------------------
		for (String vago : presentes(texto, "vague")) {
			hallazgos.add(new Hallazgo("termino-sin-magnitud", Caracteristica.VERIFICABLE,
					Gravedad.DEFECTO, vago,
					"Termino sin magnitud: no hay forma de comprobar si se cumplio. Acompanelo de "
							+ "una cifra o sustituyalo por lo que se observa"));
		}

		// --- Singular: mas de una obligacion -----------------------------
		for (String union : presentes(texto, "conjunction")) {
			hallazgos.add(new Hallazgo("posible-obligacion-doble", Caracteristica.SINGULAR,
					Gravedad.SOSPECHA, union,
					"Puede estar uniendo dos obligaciones. Si lo son, dividalas: un requisito con "
							+ "dos obligaciones no puede aprobarse ni verificarse a medias"));
		}

		// --- Singular: longitud ------------------------------------------
		int palabras = texto.split("\\s+").length;
		if (palabras > maxPalabras) {
			hallazgos.add(new Hallazgo("enunciado-extenso", Caracteristica.SINGULAR,
					Gravedad.SOSPECHA, palabras + " palabras",
					"Enunciado extenso. Por encima de " + maxPalabras + " palabras suele contener "
							+ "mas de una obligacion"));
		}

		return hallazgos;
	}

	/** Indica si el enunciado esta libre de defectos ciertos. */
	public boolean conforme(String enunciado) {
		return examinar(enunciado).stream().noneMatch(h -> h.gravedad() == Gravedad.DEFECTO);
	}

	// =================================================================

	private boolean contieneAlguno(String texto, String vocabulario) {
		return !primeroPresente(texto, vocabulario).isEmpty();
	}

	private String primeroPresente(String texto, String vocabulario) {
		for (String termino : vocabularios.getOrDefault(vocabulario, List.of())) {
			if (contienePalabra(texto, termino)) {
				return termino;
			}
		}
		return "";
	}

	private List<String> presentes(String texto, String vocabulario) {
		List<String> encontrados = new ArrayList<>();
		for (String termino : vocabularios.getOrDefault(vocabulario, List.of())) {
			if (contienePalabra(texto, termino)) {
				encontrados.add(termino);
			}
		}
		return encontrados;
	}

	/**
	 * Busca el termino como palabra completa.
	 *
	 * <p>La comprobacion por subcadena produciria falsos positivos abundantes:
	 * <em>facil</em> aparece dentro de <em>facilitador</em>, y <em>es</em> dentro
	 * de casi cualquier palabra.</p>
	 */
	private boolean contienePalabra(String texto, String termino) {
		if (termino.isEmpty()) {
			return false;
		}
		String patron = "(?<![\\p{L}\\p{N}])" + Pattern.quote(termino) + "(?![\\p{L}\\p{N}])";
		return Pattern.compile(patron).matcher(texto).find();
	}

	/** Prescinde de acentos y mayusculas para que la comparacion no dependa de ellos. */
	public static String normalizar(String texto) {
		String base = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
		base = Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		return base.replaceAll("\\s+", " ");
	}

	/** Vocabularios cargados, para poder comprobar la configuracion. */
	public Map<String, List<String>> getVocabularios() {
		return Map.copyOf(vocabularios);
	}

}
