package org.slcp.service.ingestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compara requisitos por lo que dicen, no por como se llaman.
 *
 * <p>El identificador es una etiqueta que pone quien redacta el documento: dos
 * documentos distintos numeran desde uno, y el mismo requisito puede aparecer
 * con nombres diferentes. Decidir por el identificador confunde dos cosas
 * distintas --- que dos requisitos sean el mismo, y que compartan etiqueta ---
 * y produce los dos errores posibles: descartar un requisito nuevo por llevar un
 * numero ya usado, y admitir dos veces el mismo por venir numerado distinto.</p>
 *
 * <p>La comparacion es lexica y por tanto aproximada. Por eso hay tres zonas y
 * no dos: identico, distinto, y una franja intermedia que no se resuelve sola
 * sino que se informa para que la decida una persona.</p>
 */
public final class StatementSimilarity {

	/** Por encima de esto, se consideran el mismo requisito. */
	public static final double UMBRAL_DUPLICADO = 0.82;

	/**
	 * Por encima de esto y por debajo del anterior, se informa como sospecha.
	 *
	 * <p>El valor se fijo enfrentando dos especificaciones reales del mismo
	 * sistema. En esa prueba, "almacenar cada lectura de humedad del suelo" y
	 * "almacenar cada lectura recibida de un sensor" quedaron en 0,56: son
	 * distintos en la letra y probablemente el mismo en la intencion, que es
	 * justo el caso que debe mirar una persona.</p>
	 */
	public static final double UMBRAL_SOSPECHA = 0.50;

	/**
	 * Palabras que no distinguen un requisito de otro.
	 *
	 * <p>Casi todos los enunciados de una especificacion empiezan por "el sistema
	 * debera", de modo que esas palabras suben la semejanza de cualquier par y
	 * acercan entre si a requisitos que no tienen nada que ver.</p>
	 */
	private static final Set<String> VACIAS = Set.of(
			"el", "la", "los", "las", "un", "una", "unos", "unas", "lo",
			"de", "del", "al", "a", "ante", "con", "en", "entre", "para", "por", "segun",
			"sin", "sobre", "tras", "y", "e", "o", "u", "que", "se", "su", "sus", "cada",
			"sistema", "plataforma", "aplicacion", "debera", "deberan", "debe", "deben",
			"deberia", "deberian", "podra", "podran", "puede", "es", "ser", "sera",
			"cuando", "si", "no", "mas", "menos", "todo", "toda", "todos", "todas");

	private StatementSimilarity() {
	}

	/**
	 * Semejanza entre dos enunciados, de cero a uno.
	 *
	 * <p>Se combinan dos medidas. La primera compara los conjuntos de palabras
	 * significativas; la segunda, cuanto del enunciado mas corto esta contenido en
	 * el mas largo. La segunda es necesaria porque un enunciado ampliado con
	 * detalles --- misma exigencia, mas precisa --- sigue siendo el mismo
	 * requisito, y solo con la primera medida la ampliacion lo alejaria.</p>
	 */
	public static double entre(String uno, String otro) {
		List<String> a = tokens(uno);
		List<String> b = tokens(otro);

		if (a.isEmpty() || b.isEmpty()) {
			return 0.0;
		}

		Set<String> conjuntoA = new HashSet<>(a);
		Set<String> conjuntoB = new HashSet<>(b);

		Set<String> comunes = new HashSet<>(conjuntoA);
		comunes.retainAll(conjuntoB);

		Set<String> union = new HashSet<>(conjuntoA);
		union.addAll(conjuntoB);

		double jaccard = (double) comunes.size() / union.size();
		double contencion = (double) comunes.size() / Math.min(conjuntoA.size(), conjuntoB.size());

		// Se pondera mas la contencion: importa mas que uno diga todo lo del otro
		// que el que ademas anada detalles.
		return 0.4 * jaccard + 0.6 * contencion;
	}

	/**
	 * Semejanza estricta, sin ponderar la contencion.
	 *
	 * <p>Se emplea para agrupar. La medida de {@link #entre} favorece que un
	 * enunciado contenga al otro, lo que conviene al buscar duplicados y estorba
	 * al agrupar: un requisito breve queda contenido en muchos, y arrastraria a un
	 * mismo grupo requisitos de asuntos distintos.</p>
	 */
	public static double estricta(String uno, String otro) {
		Set<String> a = new HashSet<>(tokens(uno));
		Set<String> b = new HashSet<>(tokens(otro));

		if (a.isEmpty() || b.isEmpty()) {
			return 0.0;
		}

		Set<String> comunes = new HashSet<>(a);
		comunes.retainAll(b);

		Set<String> union = new HashSet<>(a);
		union.addAll(b);

		return (double) comunes.size() / union.size();
	}

	/** Indica si ambos enunciados exigen lo mismo. */
	public static boolean sonElMismo(String uno, String otro) {
		return entre(uno, otro) >= UMBRAL_DUPLICADO;
	}

	/**
	 * Palabras significativas del enunciado, reducidas a su raiz aproximada.
	 *
	 * <p>Se recortan las terminaciones mas frecuentes del castellano para que
	 * "registrar", "registra" y "registro" cuenten como la misma palabra. Es una
	 * aproximacion tosca y deliberadamente conservadora: recortar de mas juntaria
	 * palabras que no significan lo mismo, y eso haria pasar por duplicados a
	 * requisitos distintos, que es el error mas caro de los dos.</p>
	 */
	static List<String> tokens(String texto) {
		List<String> salida = new ArrayList<>();
		if (texto == null || texto.isBlank()) {
			return salida;
		}

		String limpio = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
				.replaceAll("[^a-z0-9\\s]", " ");

		for (String palabra : limpio.split("\\s+")) {
			if (palabra.length() < 3 || VACIAS.contains(palabra)) {
				continue;
			}
			salida.add(raiz(palabra));
		}
		return salida;
	}

	private static String raiz(String palabra) {
		for (String terminacion : List.of("aciones", "acion", "amiento", "imiento",
				"antes", "entes", "ando", "endo", "arse", "erse", "irse",
				"adas", "idas", "ados", "idos", "ada", "ida", "ado", "ido",
				"ares", "eres", "ires", "ar", "er", "ir", "es", "as", "os", "a", "o", "s")) {

			if (palabra.length() > terminacion.length() + 3 && palabra.endsWith(terminacion)) {
				return palabra.substring(0, palabra.length() - terminacion.length());
			}
		}
		return palabra;
	}
}
