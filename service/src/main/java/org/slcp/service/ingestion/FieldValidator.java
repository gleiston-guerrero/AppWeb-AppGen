package org.slcp.service.ingestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comprueba los campos que llegan en un documento importado.
 *
 * <p>Que un valor venga en el archivo no significa que sea correcto. Una
 * especificacion puede traer el criterio de verificacion repitiendo el
 * enunciado, el nombre igual que el enunciado entero, una prioridad que no
 * existe, o el interesado con una frase en lugar de un papel. Nada de eso hace
 * fallar la importacion, y por eso pasa inadvertido: se guarda, y despues todo
 * lo que se calcule sobre ello hereda el error sin avisar.</p>
 *
 * <p>Aqui no se corrige ni se descarta: se senala. Corregir supondria decidir
 * que quiso decir quien lo escribio, y descartar perderia un dato que quiza solo
 * esta mal escrito. Lo que se hace es marcarlo para que una persona lo mire
 * antes de que nada dependa de el.</p>
 */
public final class FieldValidator {

	/** Lo que se encontro en un campo, y por que importa. */
	public record Reparo(String campo, String valor, String motivo, boolean grave) {
	}

	/** Prioridades reconocidas. Fuera de estas, no se puede ordenar por ella. */
	private static final Set<String> PRIORIDADES = Set.of(
			"must", "should", "could", "wont", "won't", "alta", "media", "baja", "critica",
			"obligatorio", "deseable", "opcional", "1", "2", "3", "4", "5");

	/** Un papel es un sustantivo corto, no una oracion. */
	private static final Pattern VERBO_CONJUGADO = Pattern.compile(
			"\\b(?:debera|debe|es|son|esta|estan|tiene|tienen|hace|permite|registra|puede)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final int MAXIMO_PAPEL = 60;
	private static final int MAXIMO_NOMBRE = 120;

	private FieldValidator() {
	}

	/**
	 * Comprueba los campos de un requisito recien leido.
	 *
	 * @param nombre       nombre corto declarado
	 * @param enunciado    lo que el requisito obliga
	 * @param criterio     criterio de verificacion declarado
	 * @param interesado   quien lo pidio o de donde vienen sus datos
	 * @param prioridad    prioridad declarada
	 * @param identificador identificador de origen, si lo trae
	 */
	public static List<Reparo> revisar(String nombre, String enunciado, String criterio,
			String interesado, String prioridad, String identificador) {

		List<Reparo> reparos = new ArrayList<>();

		revisarNombre(nombre, enunciado, reparos);
		revisarCriterio(criterio, enunciado, reparos);
		revisarInteresado(interesado, reparos);
		revisarPrioridad(prioridad, reparos);
		revisarTipo(identificador, enunciado, reparos);

		return reparos;
	}

	// =================================================================

	private static void revisarNombre(String nombre, String enunciado, List<Reparo> reparos) {
		if (vacio(nombre)) {
			return;
		}

		if (igualQue(nombre, enunciado)) {
			reparos.add(new Reparo("nombre", recortar(nombre),
					"El nombre repite el enunciado entero. Un nombre sirve para reconocer el "
							+ "requisito en una lista, y si es el enunciado no cumple esa funcion",
					false));
			return;
		}

		if (nombre.length() > MAXIMO_NOMBRE) {
			reparos.add(new Reparo("nombre", recortar(nombre),
					"El nombre tiene " + nombre.length() + " caracteres. A esa longitud deja de "
							+ "poder leerse de un vistazo, que es para lo unico que sirve",
					false));
		}
	}

	/**
	 * El criterio ha de decir como se comprueba, no repetir lo que se exige.
	 *
	 * <p>Es el error mas frecuente y el mas costoso: un criterio que repite el
	 * enunciado parece que el requisito esta verificado, y de el salen luego
	 * pruebas que no comprueban nada.</p>
	 */
	private static void revisarCriterio(String criterio, String enunciado, List<Reparo> reparos) {
		if (vacio(criterio)) {
			return;
		}

		if (igualQue(criterio, enunciado)) {
			reparos.add(new Reparo("verification", recortar(criterio),
					"El criterio repite el enunciado. Un criterio dice como se comprueba, no que "
							+ "se exige, y de este saldrian pruebas que no comprueban nada",
					true));
			return;
		}

		double parecido = StatementSimilarity.estricta(criterio, enunciado);
		if (parecido >= 0.70) {
			reparos.add(new Reparo("verification", recortar(criterio),
					"El criterio se parece al enunciado en un " + Math.round(parecido * 100)
							+ " por ciento. Revise que diga como se comprueba y no lo mismo con "
							+ "otras palabras",
					false));
		}

		if (criterio.trim().length() < 15) {
			reparos.add(new Reparo("verification", recortar(criterio),
					"El criterio tiene menos de quince caracteres: es dificil que describa una "
							+ "comprobacion ejecutable",
					false));
		}
	}

	/**
	 * El interesado es un papel o una fuente, no una frase.
	 *
	 * <p>No se exige que sea una persona: un sensor o un sistema externo son
	 * fuentes legitimas. Lo que se comprueba es que sea un nombre, porque una
	 * oracion en ese campo indica que el documento puso ahi otra cosa.</p>
	 */
	private static void revisarInteresado(String interesado, List<Reparo> reparos) {
		if (vacio(interesado)) {
			return;
		}

		String valor = interesado.trim();

		if (VERBO_CONJUGADO.matcher(normalizar(valor)).find()) {
			reparos.add(new Reparo("actor", recortar(valor),
					"El interesado contiene una oracion, no un papel ni una fuente. Probablemente "
							+ "el documento puso ahi otra cosa",
					true));
			return;
		}

		if (valor.length() > MAXIMO_PAPEL) {
			reparos.add(new Reparo("actor", recortar(valor),
					"El interesado tiene " + valor.length() + " caracteres. Un papel o una fuente "
							+ "se nombran en pocas palabras",
					false));
		}

		if (valor.matches("\\d+")) {
			reparos.add(new Reparo("actor", valor,
					"El interesado es un numero. No identifica a nadie", true));
		}
	}

	private static void revisarPrioridad(String prioridad, List<Reparo> reparos) {
		if (vacio(prioridad)) {
			return;
		}

		String valor = normalizar(prioridad);
		if (!PRIORIDADES.contains(valor)) {
			reparos.add(new Reparo("priority", prioridad.trim(),
					"La prioridad no es de las reconocidas. No podra ordenarse ni compararse con "
							+ "las demas mientras no se corrija",
					false));
		}
	}

	/**
	 * El prefijo del identificador declara el tipo, y puede contradecir al texto.
	 *
	 * <p>Un RNF que enuncia una funcion, o un RF que enuncia una cualidad, indican
	 * que el identificador se copio de otro sitio. Se senala y no se cambia: puede
	 * estar mal el prefijo o estarlo el enunciado.</p>
	 */
	private static void revisarTipo(String identificador, String enunciado, List<Reparo> reparos) {
		if (vacio(identificador) || vacio(enunciado)) {
			return;
		}

		// Solo las letras iniciales: "RNF-T6" tiene el numero tras una letra, y
		// cortar por el primer digito dejaria "rnf-t" en lugar de "rnf".
		String prefijo = normalizar(identificador).replaceAll("[^a-z].*$", "");
		String texto = normalizar(enunciado);

		boolean pareceCualidad = texto.contains("disponib") || texto.contains("rendimient")
				|| texto.contains("no supere") || texto.contains("menos de")
				|| texto.contains("por ciento del tiempo") || texto.contains("sin sustitucion");

		if ("rf".equals(prefijo) && pareceCualidad) {
			reparos.add(new Reparo("kind", identificador,
					"Se declara funcional pero el enunciado describe una cualidad medible. "
							+ "Compruebe si deberia ser no funcional",
					false));
		}

		boolean pareceFuncion = texto.matches(".*\\b(registrar|almacenar|exportar|mostrar|"
				+ "calcular|enviar|crear|eliminar|listar|consultar)\\b.*");

		if ("rnf".equals(prefijo) && pareceFuncion && !pareceCualidad) {
			reparos.add(new Reparo("kind", identificador,
					"Se declara no funcional pero el enunciado describe una funcion. Compruebe si "
							+ "deberia ser funcional",
					false));
		}
	}

	// =================================================================

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	private static boolean igualQue(String uno, String otro) {
		return !vacio(uno) && !vacio(otro) && normalizar(uno).equals(normalizar(otro));
	}

	private static String recortar(String texto) {
		String t = texto.trim().replaceAll("\\s+", " ");
		return t.length() <= 70 ? t : t.substring(0, 69) + "…";
	}

	private static String normalizar(String texto) {
		String base = texto == null ? "" : texto.toLowerCase(Locale.ROOT).trim()
				.replaceAll("\\s+", " ").replaceAll("[.,;:]$", "");

		return Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
	}
}
