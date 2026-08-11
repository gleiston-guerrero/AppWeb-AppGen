package org.slcp.service.ingestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slcp.service.ingestion.CriterionSuggester.Suggestion;

/**
 * Propone redacciones alternativas para un enunciado defectuoso.
 *
 * <p>Realiza ANA-14 y ANA-20 sobre el enunciado, no solo sobre el criterio. Cada
 * propuesta corrige defectos concretos detectados por {@link RequirementLinter}
 * y explica cual, de modo que quien revisa pueda juzgarla en lugar de aceptarla
 * por venir de la maquina.</p>
 *
 * <p>El limite es el mismo de siempre: se reescribe lo que el enunciado ya dice.
 * Donde falta una magnitud se deja un hueco marcado, porque que valor basta
 * depende del riesgo que se tolere y eso no esta en el requisito.</p>
 */
public final class StatementSuggester {

	/** Marca del hueco que debe rellenar una persona. */
	public static final String HUECO = "[indique la magnitud]";

	/** Sujeto que se antepone cuando el enunciado no dice quien actua. */
	private static final String SUJETO = "El sistema";

	/** Forma vinculante en castellano, equivalente al <em>shall</em> de la norma. */
	private static final String VINCULANTE = "debera";

	private final RequirementLinter linter;

	public StatementSuggester(RequirementLinter linter) {
		this.linter = linter;
	}

	/**
	 * Propone redacciones para el enunciado indicado.
	 *
	 * @return lista vacia si el enunciado ya es conforme o si no hay nada que
	 *         derivar. Devolver una propuesta generica seria peor: parece una
	 *         respuesta y se acepta sin leerla
	 */
	public List<Suggestion> proponer(String enunciado) {
		List<Suggestion> propuestas = new ArrayList<>();
		if (enunciado == null || enunciado.isBlank()) {
			return propuestas;
		}

		List<RequirementLinter.Hallazgo> hallazgos = linter.examinar(enunciado);
		if (hallazgos.isEmpty()) {
			return propuestas;
		}

		// --- Primera: correccion minima, defecto a defecto ------------------
		Correccion minima = corregir(enunciado, hallazgos);
		if (!minima.texto().equalsIgnoreCase(enunciado.trim()) && !minima.cambios().isEmpty()) {
			propuestas.add(new Suggestion(minima.texto(),
					"Correccion minima: " + String.join("; ", minima.cambios())
							+ ". Se conserva cuanto el enunciado dice.",
					minima.texto().contains(HUECO)));
		}

		// --- Segunda: division, si une dos obligaciones ---------------------
		List<String> partes = dividir(enunciado);
		if (partes.size() > 1) {
			propuestas.add(new Suggestion(partes.get(0),
					"Este enunciado une dos obligaciones y la norma exige una sola. Esta es la "
							+ "primera; la segunda debe darse de alta como requisito aparte: \""
							+ partes.get(1) + "\"",
					false));
		}

		// --- Tercera: estructura de la norma --------------------------------
		String estructurada = estructurar(minima.texto());
		if (!estructurada.equalsIgnoreCase(minima.texto())) {
			propuestas.add(new Suggestion(estructurada,
					"Ordenado segun la sintaxis de la norma: condicion, sujeto, accion, objeto y "
							+ "restriccion de la accion.",
					estructurada.contains(HUECO)));
		}

		return propuestas;
	}

	/** Texto corregido junto con la relacion de lo que se cambio. */
	private record Correccion(String texto, List<String> cambios) {
	}

	private Correccion corregir(String enunciado, List<RequirementLinter.Hallazgo> hallazgos) {
		String texto = enunciado.trim();
		List<String> cambios = new ArrayList<>();

		for (RequirementLinter.Hallazgo h : hallazgos) {
			switch (h.regla()) {
				case "verbo-no-vinculante", "verbo-permisivo" -> {
					String antes = texto;
					texto = sustituirPalabra(texto, h.evidencia(), VINCULANTE);
					if (!antes.equals(texto)) {
						cambios.add("\"" + h.evidencia() + "\" pasa a \"" + VINCULANTE
								+ "\", que es la forma que obliga");
					}
				}
				case "capacidad-en-lugar-de-accion" -> {
					String antes = texto;
					texto = texto.replaceAll("(?i)deber[aá]\\s+ser\\s+capaz\\s+de\\s+", VINCULANTE + " ")
							.replaceAll("(?i)deber[aá]\\s+poder\\s+", VINCULANTE + " ")
							.replaceAll("(?i)tendr[aá]\\s+la\\s+capacidad\\s+de\\s+", VINCULANTE + " ");
					if (!antes.equals(texto)) {
						cambios.add("se enuncia la accion en lugar de la capacidad de hacerla");
					}
				}
				case "voz-pasiva" -> {
					String antes = texto;
					texto = activar(texto);
					if (!antes.equals(texto)) {
						cambios.add("se pasa a voz activa, diciendo quien actua");
					}
				}
				case "termino-sin-magnitud" -> {
					String antes = texto;
					texto = sustituirPalabra(texto, h.evidencia(), HUECO);
					if (!antes.equals(texto)) {
						cambios.add("\"" + h.evidencia() + "\" no tiene magnitud y queda como hueco");
					}
				}
				case "sujeto-ausente" -> {
					// Se atiende al final, cuando el resto ya esta corregido.
				}
				default -> {
					// Enunciado negativo, obligacion doble y extension no se corrigen
					// aqui: exigen decidir que se quiere decir, no como decirlo.
				}
			}
		}

		if (!tieneSujeto(texto)) {
			String sinEnclitico = quitarEnclitico(texto);
			if (!sinEnclitico.equals(texto)) {
				texto = sinEnclitico;
				cambios.add("se retira el pronombre del verbo, que al anteponer el sujeto haria "
						+ "que el sistema se aplicase la accion a si mismo");
			}
			texto = anteponerSujeto(texto);
			cambios.add("se antepone el sujeto, que la norma exige nombrar");
		}

		return new Correccion(limpiar(texto), cambios);
	}

	/**
	 * Pasa a voz activa las formas mas frecuentes.
	 *
	 * <p>Se atienden solo los giros habituales y no la pasiva en general: una
	 * transformacion ambiciosa produciria frases convincentes y a veces
	 * equivocadas, que es peor que no proponer nada.</p>
	 */
	private String activar(String texto) {
		String t = texto;

		// "se requiere que sea notificado X" -> "El sistema debera notificar a X".
		// El participio ha de volverse infinitivo, o la frase queda sin verbo
		// principal. Solo se transforma la terminacion -ado, que da -ar sin
		// ambiguedad; -ido puede venir de -er o de -ir, y acertar por azar seria
		// peor que no proponer nada.
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(?i)^\\s*se\\s+requiere\\s+que\\s+sea\\s+(\\w+ado)\\s+")
				.matcher(t);
		if (m.find()) {
			String infinitivo = infinitivoDe(m.group(1));
			if (infinitivo.isEmpty()) {
				return t;
			}
			t = m.replaceFirst(SUJETO + " " + VINCULANTE + " " + infinitivo + " a ");
			return t;
		}

		t = t.replaceAll("(?i)^\\s*se\\s+requiere\\s+que\\s+", SUJETO + " " + VINCULANTE + " que ");
		t = t.replaceAll("(?i)^\\s*es\\s+requerido\\s+que\\s+", SUJETO + " " + VINCULANTE + " que ");

		// "debera ser generado X" -> "debera generar X"
		java.util.regex.Matcher m2 = java.util.regex.Pattern
				.compile("(?i)deber[a\u00e1]\\s+ser\\s+(\\w+ado)\\s+").matcher(t);
		if (m2.find()) {
			String infinitivo = infinitivoDe(m2.group(1));
			if (!infinitivo.isEmpty()) {
				t = m2.replaceFirst(VINCULANTE + " " + infinitivo + " ");
			}
		}
		return t;
	}

	/**
	 * Infinitivo de un participio terminado en -ado.
	 *
	 * @return vacio si no puede establecerse sin conjeturar
	 */
	private String infinitivoDe(String participio) {
		String p = normalizar(participio);
		return p.endsWith("ado") ? p.substring(0, p.length() - 3) + "ar" : "";
	}

	/** Divide un enunciado que une dos obligaciones. */
	private List<String> dividir(String enunciado) {
		List<String> partes = new ArrayList<>();
		String texto = limpiar(enunciado);

		for (String union : List.of(", y ademas ", " y ademas ", ", y tambien ", " y tambien ")) {
			// Se avanza la longitud de la clave NORMALIZADA, no la de la marca
			// original: normalizar recorta los espacios de los extremos, y avanzar
			// de mas se come la primera letra de lo que sigue.
			String clave = normalizar(union);
			int donde = normalizar(texto).indexOf(clave);
			if (donde > 0) {
				String primera = limpiar(texto.substring(0, donde));
				String segunda = limpiar(texto.substring(donde + clave.length()));

				if (!tieneSujeto(segunda)) {
					segunda = anteponerSujeto(segunda);
				}
				partes.add(terminar(primera));
				partes.add(terminar(segunda));
				return partes;
			}
		}
		partes.add(texto);
		return partes;
	}

	/**
	 * Reordena el enunciado a la sintaxis de la norma.
	 *
	 * <p>Solo actua cuando la condicion aparece al final. Mover una condicion al
	 * principio es una transformacion segura; reordenar sujeto, accion y objeto no
	 * lo es, y aqui una frase mal reordenada cambiaria lo que el requisito
	 * exige.</p>
	 */
	private String estructurar(String texto) {
		// Se normaliza el espaciado ANTES de buscar. Buscar sobre el texto
		// normalizado y cortar sobre el original desplaza el corte tantos
		// caracteres como espacios se hayan colapsado, y la frase pierde letras.
		String t = limpiar(texto);

		for (String marca : List.of(" cuando ", " siempre que ", " en caso de que ", " si ")) {
			String clave = normalizar(marca);
			int donde = normalizar(t).lastIndexOf(clave);
			if (donde > 10) {
				String principal = limpiar(t.substring(0, donde));
				String condicion = limpiar(t.substring(donde + clave.length()));
				if (condicion.length() < 5) {
					continue;
				}
				String cabeza = clave;
				return terminar(mayuscula(cabeza) + " " + quitarPunto(condicion) + ", "
						+ minuscula(quitarPunto(principal)));
			}
		}
		return t;
	}

	// =================================================================

	/**
	 * Retira el pronombre pegado al infinitivo.
	 *
	 * <p>"Debera almacenarse el historial" sin sujeto es una pasiva refleja. Al
	 * anteponer el sujeto queda "El sistema debera almacenarse el historial", que
	 * dice que el sistema se almacena a si mismo. Retirar el pronombre restituye
	 * lo que el enunciado queria decir.</p>
	 */
	private String quitarEnclitico(String texto) {
		return texto.replaceAll("(?i)(deber[a\u00e1]n?\\s+\\w+[aei]r)se\\b", "$1");
	}

	private boolean tieneSujeto(String texto) {
		String t = normalizar(texto);
		for (String sujeto : List.of("el sistema", "la plataforma", "la aplicacion", "el modulo",
				"el componente", "el servicio")) {
			if (t.contains(sujeto)) {
				return true;
			}
		}
		return false;
	}

	private String anteponerSujeto(String texto) {
		String t = limpiar(texto);
		String n = normalizar(t);

		// Si el enunciado empieza por el verbo, el sujeto encaja delante sin mas.
		if (n.startsWith("debera") || n.startsWith("debe") || n.startsWith("deberan")) {
			return SUJETO + " " + minuscula(t);
		}
		// Si empieza por una condicion, el sujeto va tras ella.
		for (String marca : List.of("cuando ", "siempre que ", "en caso de que ", "si ")) {
			if (n.startsWith(marca)) {
				int coma = t.indexOf(',');
				if (coma > 0) {
					return t.substring(0, coma + 1) + " " + minuscula(SUJETO) + " "
							+ minuscula(t.substring(coma + 1).trim());
				}
			}
		}
		return SUJETO + " " + VINCULANTE + " " + minuscula(t);
	}

	private String sustituirPalabra(String texto, String termino, String reemplazo) {
		if (termino == null || termino.isBlank()) {
			return texto;
		}
		// Se compara sin acentos, de modo que se sustituye tanto "rapida" como
		// "rápida" sin declarar las dos formas.
		StringBuilder salida = new StringBuilder();
		String[] palabras = texto.split("(?=\\s)|(?<=\\s)");
		for (String palabra : palabras) {
			String limpia = normalizar(palabra).replaceAll("[.,;:]", "");
			if (limpia.equals(normalizar(termino))) {
				salida.append(palabra.replaceAll("(?i)[\\p{L}]+", reemplazo));
			} else {
				salida.append(palabra);
			}
		}
		return salida.toString();
	}

	private String limpiar(String texto) {
		return texto.trim().replaceAll("\\s+", " ").replaceAll("^[,;.\\s]+", "");
	}

	private String terminar(String texto) {
		String t = limpiar(texto);
		return t.endsWith(".") ? t : t + ".";
	}

	private String quitarPunto(String texto) {
		String t = limpiar(texto);
		return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
	}

	private String mayuscula(String texto) {
		return texto.isEmpty() ? texto
				: Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}

	private String minuscula(String texto) {
		return texto.isEmpty() ? texto
				: Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
	}

	private static String normalizar(String texto) {
		String base = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
		base = Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		return base.replaceAll("\\s+", " ");
	}
}
