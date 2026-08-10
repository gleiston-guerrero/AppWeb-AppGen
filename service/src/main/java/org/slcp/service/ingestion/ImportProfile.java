package org.slcp.service.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Descripcion de como esta escrito un documento de requisitos.
 *
 * <p>El formato del documento es dato y no codigo, por el mismo motivo que
 * TGT-14 exige que el dominio de la aplicacion generada sea dato: si el formato
 * quedara incrustado en el lector, cada especificacion con otra plantilla
 * obligaria a programar. Con perfiles, obliga a describir.</p>
 *
 * <p>El perfil se declara en texto plano, una directiva por linea:</p>
 *
 * <pre>
 * profile.id     = latex-atributos
 * block.begin    = \begin{atributos}
 * block.end      = \end{atributos}
 * id.pattern     = \\begin\\{atributos\\}\\{([^}]+)\\}
 * row.separator  = &amp;
 * row.terminator = \\\\
 * field.Identificador = id
 * field.Nombre        = name
 * expected            = id, name, description
 * </pre>
 */
public final class ImportProfile {

	/** El bloque termina donde empieza el siguiente. Para formatos sin marca de cierre. */
	public static final String FIN_SIGUIENTE = "<NEXT>";

	/** El bloque termina en la primera linea vacia. */
	public static final String FIN_LINEA_VACIA = "<BLANK>";

	private final String id;
	private final String name;
	private final String description;
	private final List<String> extensions;
	private final String example;
	private final String reader;
	private final Map<String, String> ajustes;
	private final String blockBegin;
	private final String blockEnd;
	private final Pattern idPattern;
	private final Pattern namePattern;
	private final String rowSeparator;
	private final String rowTerminator;
	private final Map<String, String> fieldMap;
	private final List<String> expected;

	private ImportProfile(String id, String name, String description, List<String> extensions,
			String example, String reader, Map<String, String> ajustes,
			String blockBegin, String blockEnd, Pattern idPattern,
			Pattern namePattern, String rowSeparator, String rowTerminator, Map<String, String> fieldMap,
			List<String> expected) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.extensions = List.copyOf(extensions);
		this.example = example;
		this.reader = reader;
		this.ajustes = Collections.unmodifiableMap(ajustes);
		this.blockBegin = blockBegin;
		this.blockEnd = blockEnd;
		this.idPattern = idPattern;
		this.namePattern = namePattern;
		this.rowSeparator = rowSeparator;
		this.rowTerminator = rowTerminator;
		this.fieldMap = Collections.unmodifiableMap(fieldMap);
		this.expected = List.copyOf(expected);
	}

	/**
	 * Carga un perfil desde su declaracion.
	 *
	 * @throws IllegalArgumentException si falta alguna directiva obligatoria
	 */
	public static ImportProfile cargar(Reader origen) throws IOException {
		Map<String, String> directivas = new LinkedHashMap<>();
		Map<String, String> campos = new LinkedHashMap<>();
		StringBuilder ejemplo = new StringBuilder();

		try (BufferedReader lector = new BufferedReader(origen)) {
			String linea;
			boolean enEjemplo = false;

			while ((linea = lector.readLine()) != null) {
				// El ejemplo ocupa varias lineas y se conserva tal cual, incluidos
				// sangrado y lineas en blanco: es lo que la persona va a comparar con
				// su archivo, y reformatearlo lo haria menos util.
				if (linea.trim().equals("example.begin")) {
					enEjemplo = true;
					continue;
				}
				if (linea.trim().equals("example.end")) {
					enEjemplo = false;
					continue;
				}
				if (enEjemplo) {
					ejemplo.append(linea).append('\n');
					continue;
				}

				String limpia = linea.trim();
				if (limpia.isEmpty() || limpia.startsWith("#")) {
					continue;
				}
				int igual = limpia.indexOf('=');
				if (igual < 0) {
					continue;
				}
				String clave = limpia.substring(0, igual).trim();
				String valor = limpia.substring(igual + 1).trim();

				if (clave.startsWith("field.")) {
					campos.put(normalizar(clave.substring("field.".length())), valor);
				} else {
					directivas.put(clave, valor);
				}
			}
		}

		exigir(directivas, "profile.id");
		// Solo el lector de lineas necesita delimitadores y patron de identificador.
		// Exigirselos a un lector estructurado obligaria a declarar directivas que
		// no significan nada para el.
		if ("line".equals(directivas.getOrDefault("profile.reader", "line"))) {
			exigir(directivas, "block.begin");
			exigir(directivas, "block.end");
			exigir(directivas, "id.pattern");
		}

		List<String> esperados = new ArrayList<>();
		String lista = directivas.getOrDefault("expected", "");
		for (String parte : lista.split(",")) {
			if (!parte.isBlank()) {
				esperados.add(parte.trim());
			}
		}

		List<String> extensiones = new ArrayList<>();
		for (String parte : directivas.getOrDefault("profile.extensions", "").split(",")) {
			if (!parte.isBlank()) {
				extensiones.add(parte.trim());
			}
		}

		return new ImportProfile(
				directivas.get("profile.id"),
				directivas.getOrDefault("profile.name", directivas.get("profile.id")),
				directivas.getOrDefault("profile.description", ""),
				extensiones,
				ejemplo.toString().stripTrailing(),
				directivas.getOrDefault("profile.reader", "line"),
				directivas,
				directivas.getOrDefault("block.begin", "<sin bloque>"),
				directivas.getOrDefault("block.end", ""),
				Pattern.compile(directivas.getOrDefault("id.pattern", "(?!)")),
				directivas.containsKey("name.pattern")
						? Pattern.compile(directivas.get("name.pattern"))
						: null,
				directivas.getOrDefault("row.separator", "&"),
				directivas.getOrDefault("row.terminator", "\\\\"),
				campos,
				esperados);
	}

	private static void exigir(Map<String, String> directivas, String clave) {
		if (!directivas.containsKey(clave) || directivas.get(clave).isBlank()) {
			throw new IllegalArgumentException(
					"El perfil no declara la directiva obligatoria: " + clave);
		}
	}

	/**
	 * Normaliza una etiqueta para compararla.
	 *
	 * <p>Se prescinde de mayusculas, acentos y espacios sobrantes porque un mismo
	 * campo aparece escrito de varias maneras a lo largo de un documento real, y
	 * exigir coincidencia exacta convertiria cada variante tipografica en un
	 * campo desconocido.</p>
	 */
	public static String normalizar(String etiqueta) {
		String texto = etiqueta == null ? "" : etiqueta.trim().toLowerCase(Locale.ROOT);
		texto = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

		// Se descarta el marcado que rodea a la etiqueta. Sin esto, un mismo campo
		// escrito como Descripcion, **Descripcion** o - Descripcion serian tres
		// campos distintos, y dos de ellos quedarian sin reconocer.
		texto = texto.replaceAll("^[\\s\\-*_#>|]+", "")
				.replaceAll("[\\s*_:|]+$", "");

		return texto.replaceAll("\\s+", " ").trim();
	}

	/** Campo canonico al que corresponde una etiqueta, o vacio si no se reconoce. */
	public String campoDe(String etiqueta) {
		return fieldMap.getOrDefault(normalizar(etiqueta), "");
	}

	public String getId() {
		return id;
	}

	/** Lector que corresponde a este formato: line, json, yaml, xml o csv. */
	public String getReader() {
		return reader;
	}

	/** Ajuste propio del lector, con su valor por defecto. */
	public String ajuste(String clave, String porDefecto) {
		String valor = ajustes.get(clave);
		return (valor == null || valor.isBlank()) ? porDefecto : valor;
	}

	public String getName() {
		return name;
	}

	/** Indica si el formato tiene estructura propia y exige un analizador. */
	public boolean esEstructurado() {
		return !"line".equals(reader);
	}

	/** Ruta hasta la lista de requisitos dentro del documento, si la hay. */
	public String getListPath() {
		return ajuste("list.path", "");
	}

	public String getDescription() {
		return description;
	}

	public List<String> getExtensions() {
		return extensions;
	}

	/** Ejemplo del formato esperado, para mostrarlo a quien va a subir un archivo. */
	public String getExample() {
		return example;
	}

	/** Indica si el bloque se cierra donde empieza el siguiente. */
	public boolean cierraEnSiguiente() {
		return FIN_SIGUIENTE.equals(blockEnd);
	}

	/** Indica si el bloque se cierra en la primera linea vacia. */
	public boolean cierraEnLineaVacia() {
		return FIN_LINEA_VACIA.equals(blockEnd);
	}

	public String getBlockBegin() {
		return blockBegin;
	}

	public String getBlockEnd() {
		return blockEnd;
	}

	public Pattern getIdPattern() {
		return idPattern;
	}

	/**
	 * Patron del nombre en la cabecera del bloque, o nulo si no lo hay.
	 *
	 * <p>Algunos formatos ponen el nombre en el encabezado en lugar de en un campo
	 * propio. Sin este patron, esos requisitos entrarian sin nombre y pareceria
	 * una carencia del documento cuando el nombre estaba a la vista.</p>
	 */
	public Pattern getNamePattern() {
		return namePattern;
	}

	public String getRowSeparator() {
		return rowSeparator;
	}

	public String getRowTerminator() {
		return rowTerminator;
	}

	public List<String> getExpected() {
		return expected;
	}

	public Map<String, String> getFieldMap() {
		return fieldMap;
	}
}
