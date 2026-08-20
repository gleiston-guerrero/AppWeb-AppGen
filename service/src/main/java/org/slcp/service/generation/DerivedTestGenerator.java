package org.slcp.service.generation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genera pruebas derivandolas del propio requisito.
 *
 * <p>Es la implementacion de por defecto y no necesita nada externo. Deriva lo
 * que el requisito ya dice: su criterio de verificacion es una prueba en prosa,
 * y convertirlo en una prueba escrita es una traduccion, no una invencion.</p>
 *
 * <p>Donde el requisito no dice, aqui se deja un hueco marcado en lugar de
 * rellenarlo. Una prueba con un umbral inventado pasa o falla por una cifra que
 * nadie decidio, y lo hace en silencio: es peor que no tenerla, porque parece
 * que ese requisito esta comprobado.</p>
 */
public final class DerivedTestGenerator implements TestGenerator {

	/** Marca de lo que ha de rellenar una persona. */
	public static final String HUECO = "[indique el valor]";

	/** Clases de prueba que sabe derivar. */
	public static final String ACEPTACION = "ACCEPTANCE";
	public static final String LIMITE = "BOUNDARY";
	public static final String NEGATIVA = "NEGATIVE";
	public static final String RENDIMIENTO = "PERFORMANCE";

	private static final Pattern CIFRA = Pattern.compile(
			"(\\d+(?:[.,]\\d+)?)\\s*(por ciento|%|segundos?|minutos?|horas?|dias?|litros?|"
					+ "metros?|kilogramos?|grados?|hectareas?|registros?|usuarios?)");

	/** Condiciones que anuncian un disparador: lo que hay antes es la premisa. */
	private static final List<String> DISPARADORES = List.of(
			"cuando ", "siempre que ", "en caso de que ", "si ");

	@Override
	public List<String> clases() {
		return List.of(ACEPTACION, LIMITE, NEGATIVA, RENDIMIENTO);
	}

	@Override
	public List<ArtifactProposal> generar(RequirementInput r, String clase) {
		return switch (clase) {
			case ACEPTACION -> List.of(aceptacion(r));
			case LIMITE -> limite(r);
			case NEGATIVA -> List.of(negativa(r));
			case RENDIMIENTO -> rendimiento(r);
			default -> List.of();
		};
	}

	// =================================================================

	/**
	 * Prueba de aceptacion: la traduccion del criterio a un escenario.
	 *
	 * <p>Un criterio de verificacion bien escrito ya trae las tres partes de un
	 * escenario --- "Con X, hacer Y y comprobar que Z" ---, de modo que lo que hay
	 * que hacer es leerlas, no pegar el criterio entero en el resultado esperado.
	 * Pegarlo produce un "Entonces" que repite la accion y esconde lo unico que se
	 * comprueba.</p>
	 *
	 * <p>Se escribe en Gherkin porque es el formato que quien responde del sistema
	 * puede leer sin saber programar, y estas pruebas existen justamente para que
	 * las juzgue quien no programa.</p>
	 */
	private ArtifactProposal aceptacion(RequirementInput r) {
		Escenario e = despiezar(r);

		StringBuilder texto = new StringBuilder();
		texto.append("# Generada de ").append(r.etiqueta()).append('\n');
		texto.append("Caracteristica: ").append(nombreDe(r)).append("\n\n");
		texto.append("  Escenario: ").append(e.titulo()).append('\n');

		boolean primerDado = true;
		for (String dado : e.dados()) {
			// "Dado que se parte de X" concuerda con cualquier X, singular o plural.
			// "Dado datos validos" no concuerda, y "Dados" obligaria a adivinar el
			// numero del sintagma.
			texto.append("    ").append(primerDado ? "Dado que se parte de " : "Y de ")
					.append(dado).append('\n');
			primerDado = false;
		}

		texto.append("    Cuando se ").append(conjugar(e.cuando())).append('\n');

		// Una afirmacion por linea: si la prueba falla, se sabe cual fallo.
		boolean primera = true;
		for (String entonces : e.entonces()) {
			texto.append("    ").append(primera ? "Entonces " : "Y ").append(entonces).append('\n');
			primera = false;
		}

		return new ArtifactProposal(ACEPTACION,
				"Aceptacion de " + r.etiqueta(),
				texto.toString(),
				"GHERKIN",
				e.derivadoDelCriterio()
						? "Despiezada del criterio de verificacion, que ya traia el contexto, la "
								+ "accion y el resultado esperado"
						: "El requisito no tiene criterio de verificacion, de modo que el resultado "
								+ "esperado queda como hueco: derivarlo del enunciado seria inventar "
								+ "que basta para darlo por cumplido",
				e.tieneHuecos(),
				List.of(r.readableId()));
	}

	/** Las tres partes de un escenario, con lo que las justifica. */
	private record Escenario(String titulo, List<String> dados, String cuando,
			List<String> entonces, boolean derivadoDelCriterio, boolean tieneHuecos) {
	}

	/** Contexto que el criterio antepone: "Con un umbral del 30 por ciento, …". */
	private static final Pattern CONTEXTO = Pattern.compile("^con\\s+(.+?),\\s*(.+)$",
			Pattern.CASE_INSENSITIVE);

	/** Resultado esperado: "… y comprobar que Z". */
	private static final Pattern RESULTADO = Pattern.compile(
			"\\s+y\\s+(?:comprobar|verificar|confirmar|validar|observar)\\s+que\\s+(.+)$",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern SOLO_RESULTADO = Pattern.compile(
			"^(?:comprobar|verificar|confirmar|validar|observar)\\s+que\\s+(.+)$",
			Pattern.CASE_INSENSITIVE);

	/** Varias afirmaciones encadenadas en el resultado. */
	private static final Pattern OTRA_AFIRMACION = Pattern.compile(
			"\\s+y\\s+(?=que\\s)", Pattern.CASE_INSENSITIVE);

	/**
	 * Lee las tres partes del criterio.
	 *
	 * <p>Si el criterio no las trae, se cae a lo que se pueda derivar del enunciado
	 * y el resto queda como hueco. Nunca se rellena por conjetura: una prueba con
	 * un resultado inventado pasa o falla por algo que nadie decidio.</p>
	 */
	private Escenario despiezar(RequirementInput r) {
		if (!r.tieneCriterio()) {
			return new Escenario("Camino principal, sin resultado esperado declarado",
					List.of("las condiciones que exige " + r.etiqueta()),
					accionDe(r), List.of(HUECO), false, true);
		}

		String texto = r.verification().trim().replaceAll("\\.$", "");
		List<String> dados = new ArrayList<>();

		Matcher contexto = CONTEXTO.matcher(texto);
		if (contexto.matches()) {
			dados.add(contexto.group(1).trim());
			texto = contexto.group(2).trim();
		}

		List<String> entonces = new ArrayList<>();
		Matcher resultado = RESULTADO.matcher(texto);

		if (resultado.find()) {
			entonces.addAll(afirmaciones(resultado.group(1)));
			texto = texto.substring(0, resultado.start()).trim();

		} else {
			Matcher solo = SOLO_RESULTADO.matcher(texto);
			if (solo.matches()) {
				entonces.addAll(afirmaciones(solo.group(1)));
				texto = "";
			}
		}

		// Sin accion legible en el criterio se toma la del enunciado: el criterio
		// dice como se comprueba, y a veces da por sabido que se hace.
		String cuando = texto.isBlank() ? accionDe(r) : minusculaInicial(texto);

		if (dados.isEmpty()) {
			dados.add("las condiciones que exige " + r.etiqueta());
		}
		if (entonces.isEmpty()) {
			entonces.add(HUECO);
		}

		return new Escenario(tituloDe(cuando, entonces), dados, cuando, entonces, true,
				entonces.contains(HUECO));
	}

	/**
	 * Titulo del escenario.
	 *
	 * <p>Describe el caso concreto y no repite el nombre de la caracteristica: si
	 * ambos dicen lo mismo, al anadir un segundo escenario no habria como
	 * distinguirlos.</p>
	 */
	private String tituloDe(String cuando, List<String> entonces) {
		String base = entonces.contains(HUECO)
				? "Camino principal, sin resultado esperado declarado"
				: "Se " + conjugar(cuando) + " y " + entonces.get(0);

		String recortado = base.length() <= 90 ? base : base.substring(0, 89) + "…";
		return Character.toUpperCase(recortado.charAt(0)) + recortado.substring(1);
	}

	/** Separa las afirmaciones encadenadas: "que A y que B". */
	private List<String> afirmaciones(String texto) {
		List<String> salida = new ArrayList<>();
		for (String parte : OTRA_AFIRMACION.split(texto.trim())) {
			String limpia = parte.replaceAll("^que\\s+", "").trim();
			if (!limpia.isBlank()) {
				salida.add(limpia);
			}
		}
		return salida.isEmpty() ? List.of(texto.trim()) : salida;
	}

	/**
	 * Pone en presente el verbo que abre la accion.
	 *
	 * <p>El criterio se escribe en infinitivo --- "registrar una parcela" --- y un
	 * escenario se lee en presente. Se conjuga la tercera persona regular, que es
	 * la que cubre practicamente todos los verbos de una especificacion; si el
	 * verbo no termina en infinitivo, se deja como esta antes que estropearlo.</p>
	 */
	private String conjugar(String accion) {
		Matcher m = Pattern.compile("^([a-zA-ZáéíóúñÁÉÍÓÚÑ]+?)(ar|er|ir)\\b(.*)$",
				Pattern.DOTALL).matcher(accion.trim());

		if (!m.matches()) {
			return accion;
		}

		String raiz = m.group(1);
		String terminacion = m.group(2);
		String resto = m.group(3);

		// Los verbos en -iar y -uar llevan tilde en la tercera persona: envia se
		// escribe envia con tilde, y sin ella se lee como otro tiempo.
		if ("ar".equals(terminacion) && raiz.endsWith("i")) {
			raiz = raiz.substring(0, raiz.length() - 1) + "í";
		} else if ("ar".equals(terminacion) && raiz.endsWith("u")) {
			raiz = raiz.substring(0, raiz.length() - 1) + "ú";
		}

		String verbo = raiz + ("ar".equals(terminacion) ? "a" : "e");

		// En la pasiva refleja el verbo concuerda con su complemento: "se envian
		// lecturas", no "se envia lecturas". Se mira si el sintagma que sigue es
		// plural, que es lo que decide el numero.
		return esPlural(resto) ? verbo + "n" + resto : verbo + resto;
	}

	/**
	 * Indica si el complemento es plural.
	 *
	 * <p>Se mira el determinante, que es lo que lo marca sin ambiguedad. Un
	 * sustantivo suelto terminado en ese puede ser singular --- "el analisis" ---
	 * y decidir por la terminacion se equivocaria en esos casos.</p>
	 */
	private boolean esPlural(String complemento) {
		Matcher m = Pattern.compile("^\\s+(los|las|unos|unas|dos|tres|varios|varias|\\d+)\\b",
				Pattern.CASE_INSENSITIVE).matcher(complemento);

		if (m.find()) {
			return true;
		}

		// Sin determinante, un sustantivo en plural tambien lo marca: "se envian
		// lecturas".
		Matcher suelto = Pattern.compile("^\\s+([a-zA-ZáéíóúñÁÉÍÓÚÑ]+)\\b").matcher(complemento);
		return suelto.find() && suelto.group(1).length() > 4
				&& suelto.group(1).toLowerCase(Locale.ROOT).endsWith("s");
	}

	private String minusculaInicial(String texto) {
		return texto.isEmpty() ? texto
				: Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
	}

	/**
	 * Pruebas de limite: una por cada magnitud que el requisito declara.
	 *
	 * <p>Solo se generan si el requisito trae cifras. Sin ellas no hay limite que
	 * probar, y fabricar uno seria elegir por quien responde del sistema.</p>
	 */
	private List<ArtifactProposal> limite(RequirementInput r) {
		List<ArtifactProposal> salida = new ArrayList<>();
		String fuente = (r.statement() + " " + (r.verification() == null ? "" : r.verification()));

		Matcher m = CIFRA.matcher(normalizar(fuente));
		int n = 0;

		while (m.find()) {
			n++;
			String valor = m.group(1);
			String unidad = m.group(2);

			StringBuilder texto = new StringBuilder();
			texto.append("# Generada de ").append(r.etiqueta())
					.append(" — limite de ").append(valor).append(' ').append(unidad).append("\n\n");
			texto.append("  Esquema del escenario: comportamiento en el limite de ")
					.append(valor).append(' ').append(unidad).append('\n');
			texto.append("    Dado que se cumple: ").append(premisaDe(r)).append('\n');
			texto.append("    Cuando el valor observado es <valor>\n");
			texto.append("    Entonces el sistema <comportamiento>\n\n");
			texto.append("    Ejemplos:\n");
			texto.append("      | valor            | comportamiento |\n");
			texto.append("      | justo por debajo | ").append(HUECO).append(" |\n");
			texto.append("      | exactamente ").append(valor).append(" | ").append(HUECO).append(" |\n");
			texto.append("      | justo por encima | ").append(HUECO).append(" |\n");

			salida.add(new ArtifactProposal(LIMITE,
					"Limite de " + valor + " " + unidad + " en " + r.etiqueta(),
					texto.toString(),
					"GHERKIN",
					"El requisito declara " + valor + " " + unidad + ", de modo que hay un limite que "
							+ "probar. Que ocurre exactamente en el limite lo dice el requisito solo a "
							+ "veces, y por eso el comportamiento queda como hueco",
					true,
					List.of(r.readableId())));

			if (n == 3) {
				break;
			}
		}
		return salida;
	}

	/**
	 * Prueba negativa: que el sistema rechace lo que no cumple la premisa.
	 *
	 * <p>Un requisito dice lo que el sistema debe hacer y casi nunca que debe
	 * ocurrir cuando no se dan las condiciones, de modo que el resultado esperado
	 * queda como hueco. Es la carencia mas frecuente de una especificacion, y
	 * generar esta prueba la pone a la vista.</p>
	 */
	private ArtifactProposal negativa(RequirementInput r) {
		// Si hay caso de uso aceptado, sus flujos de excepcion dicen justamente lo
		// que el requisito calla: que ocurre cuando la condicion no se cumple. Eso
		// lo decidio el equipo, de modo que no hay nada que inventar ni que dejar
		// como hueco.
		List<String> excepciones = excepcionesDe(r);

		if (!excepciones.isEmpty()) {
			return deExcepcion(r, excepciones);
		}

		StringBuilder texto = new StringBuilder();
		texto.append("# Generada de ").append(r.etiqueta()).append(" — camino negativo\n\n");
		texto.append("  Escenario: no se cumple la condicion de ").append(nombreDe(r)).append('\n');
		texto.append("    Dado que NO se cumple: ").append(premisaDe(r)).append('\n');
		texto.append("    Cuando ").append(accionDe(r)).append('\n');
		texto.append("    Entonces ").append(HUECO).append('\n');
		texto.append("    Y no se produce el efecto descrito en ").append(r.etiqueta()).append('\n');

		return new ArtifactProposal(NEGATIVA,
				"Camino negativo de " + r.etiqueta(),
				texto.toString(),
				"GHERKIN",
				"El requisito dice que hace el sistema cuando se dan las condiciones, y no que ha de "
						+ "ocurrir cuando no se dan. Esa decision falta en la especificacion y por eso "
						+ "el resultado queda como hueco",
				true,
				List.of(r.readableId()));
	}

	/**
	 * Prueba de rendimiento: solo para requisitos no funcionales con plazo.
	 *
	 * <p>Se genera unicamente si el requisito trae una cifra de tiempo o de
	 * proporcion. Un requisito no funcional sin magnitud no es medible, y lo que
	 * hace falta ahi no es una prueba sino corregir el requisito.</p>
	 */
	private List<ArtifactProposal> rendimiento(RequirementInput r) {
		if (!"NON_FUNCTIONAL".equals(r.kind())) {
			return List.of();
		}

		String fuente = normalizar(r.statement() + " " + (r.verification() == null ? "" : r.verification()));
		Matcher m = CIFRA.matcher(fuente);

		if (!m.find()) {
			return List.of(new ArtifactProposal(RENDIMIENTO,
					"Sin magnitud medible: " + r.etiqueta(),
					"# " + r.etiqueta() + " no declara ninguna magnitud.\n"
							+ "# Sin ella no hay nada que medir, y lo que falta no es una prueba sino\n"
							+ "# una cifra en el requisito.\n\n"
							+ "# Enunciado: " + r.statement() + "\n",
					"TEXT",
					"Es un requisito no funcional sin magnitud. Generar una prueba exigiria inventar "
							+ "el umbral, y entonces pasaria o fallaria por una cifra que nadie decidio",
					true,
					List.of(r.readableId())));
		}

		String valor = m.group(1);
		String unidad = m.group(2);

		StringBuilder texto = new StringBuilder();
		texto.append("# Generada de ").append(r.etiqueta()).append(" — medida de ")
				.append(valor).append(' ').append(unidad).append("\n\n");
		texto.append("  Escenario: se cumple la exigencia de ").append(valor).append(' ')
				.append(unidad).append('\n');
		texto.append("    Dado un sistema en las condiciones de operacion previstas\n");
		texto.append("    Y una carga de ").append(HUECO).append(" simultanea\n");
		texto.append("    Cuando se repite la operacion ").append(HUECO).append(" veces\n");
		texto.append("    Entonces la medida no supera ").append(valor).append(' ')
				.append(unidad).append('\n');

		return List.of(new ArtifactProposal(RENDIMIENTO,
				"Medida de " + valor + " " + unidad + " en " + r.etiqueta(),
				texto.toString(),
				"GHERKIN",
				"El requisito declara " + valor + " " + unidad + ", que es lo que hay que medir. La "
						+ "carga y el numero de repeticiones no los dice el requisito y quedan como "
						+ "huecos: de ellos depende que la medida signifique algo",
				true,
				List.of(r.readableId())));
	}

	// =================================================================
	/**
	 * Prueba derivada de los flujos de excepcion del caso de uso.
	 *
	 * <p>Sale entera: la condicion y la respuesta las escribio el equipo y estan
	 * aceptadas. No queda hueco porque no falta ninguna decision.</p>
	 */
	private ArtifactProposal deExcepcion(RequirementInput r, List<String> excepciones) {
		StringBuilder texto = new StringBuilder();
		texto.append("# Generada de ").append(r.etiqueta())
				.append(" y de su caso de uso aceptado\n");
		texto.append("Caracteristica: ").append(nombreDe(r)).append("\n");

		for (String excepcion : excepciones) {
			String[] partes = excepcion.split("\u0001", 2);

			// "(paso 2)" remite al caso de uso y aqui no significa nada: quien ejecuta
			// la prueba no tiene ese documento delante.
			String condicion = minusculaInicial(
					partes[0].replaceAll("\\s*\\(paso \\d+\\)", "").trim());

			// La condicion del flujo describe un estado ---"falta la superficie"---,
			// no una accion. Va al "Dado"; la accion es la del caso de uso.
			texto.append("\n  Escenario: ").append(condicion).append('\n');
			texto.append("    Dado que ").append(condicion).append('\n');
			texto.append("    Cuando se ").append(conjugar(accionDe(r))).append('\n');
			texto.append("    Entonces ")
					.append(partes.length > 1
							? minusculaInicial(partes[1].replaceAll(
									"\\.?\\s*El flujo retorna al paso \\d+\\.?", "").trim())
							: HUECO)
					.append('\n');
		}

		return new ArtifactProposal(NEGATIVA,
				"Camino negativo de " + r.etiqueta(),
				texto.toString(),
				"GHERKIN",
				"Derivada de los " + excepciones.size() + " flujos de excepcion del caso de uso "
						+ "aceptado. La condicion y la respuesta las decidio el equipo, de modo que "
						+ "no queda nada por inventar",
				false,
				List.of(r.readableId()));
	}

	/**
	 * Condicion y respuesta de cada flujo de excepcion del caso de uso.
	 *
	 * <p>Se leen del documento con una lectura tolerante: interpretarlo entero
	 * obligaria a que la forma no cambiara nunca, y un cambio menor dejaria de dar
	 * excepciones sin avisar.</p>
	 */
	private List<String> excepcionesDe(RequirementInput r) {
		if (!r.tieneCasoDeUso()) {
			return List.of();
		}

		Matcher bloque = Pattern.compile("\"flujosExcepcionales\"\\s*:\\s*\\[(.*?)\\]",
				Pattern.DOTALL).matcher(r.useCase());

		if (!bloque.find()) {
			return List.of();
		}

		List<String> salida = new ArrayList<>();
		Matcher uno = Pattern.compile(
				"\"condicion\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"respuesta\"\\s*:\\s*\"([^\"]*)\"")
				.matcher(bloque.group(1));

		while (uno.find()) {
			salida.add(uno.group(1).trim() + "\u0001" + uno.group(2).trim());
		}
		return salida;
	}



	/**
	 * Premisa: la condicion que el requisito enuncia, o una generica.
	 *
	 * <p>Se antepone "que se cumple" al usarla en lugar de encajarla en la frase:
	 * la condicion viene en subjuntivo --- "cuando la humedad descienda" --- y
	 * ponerla tras "Dado" daria "Dado la humedad descienda". Convertir el modo
	 * verbal seria adivinar; anteponer una formula que siempre encaja, no.</p>
	 */
	private String premisaDe(RequirementInput r) {
		String texto = r.statement();
		String plano = normalizar(texto);

		for (String marca : DISPARADORES) {
			int donde = plano.indexOf(marca);
			if (donde == 0) {
				int coma = texto.indexOf(',');
				if (coma > 0) {
					return limpiar(texto.substring(marca.length(), coma));
				}
			}
			if (donde > 0) {
				return limpiar(texto.substring(donde + marca.length()));
			}
		}
		return "el sistema esta en las condiciones previstas para " + r.etiqueta();
	}

	/** Accion: lo que el requisito obliga a hacer. */
	private String accionDe(RequirementInput r) {
		String texto = r.statement();
		Matcher m = Pattern.compile("(?i)deber[aá]n?\\s+(.+?)(?:\\.|$)").matcher(texto);

		if (m.find()) {
			return limpiar(m.group(1));
		}
		return limpiar(texto);
	}

	private String nombreDe(RequirementInput r) {
		return r.name() == null || r.name().isBlank() ? r.etiqueta() : r.name();
	}

	private String limpiar(String texto) {
		String t = texto.trim().replaceAll("\\s+", " ");
		if (t.endsWith(".")) {
			t = t.substring(0, t.length() - 1);
		}
		return t;
	}

	private static String normalizar(String texto) {
		String base = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
	}
}
