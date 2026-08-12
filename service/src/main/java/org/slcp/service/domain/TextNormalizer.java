package org.slcp.service.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Unifica la forma de los textos antes de guardarlos.
 *
 * <p>Un mismo requisito escrito por tres personas llega en tres formas: todo en
 * mayusculas, todo en minusculas, con espacios de mas. Guardarlo tal cual hace
 * que la lista parezca desordenada aunque su contenido sea correcto, y estorba
 * a cuanto compara textos --- duplicados, dominio --- que tendria que normalizar
 * por su cuenta cada vez.</p>
 *
 * <p>No se corrige la redaccion: eso lo propone el sugeridor y lo decide una
 * persona. Aqui solo se unifica la forma.</p>
 */
public final class TextNormalizer {

	private TextNormalizer() {
	}

	/**
	 * Normaliza un nombre o titulo.
	 *
	 * <p>Se recorta, se colapsan los espacios y se ajusta la caja cuando el texto
	 * viene entero en mayusculas o entero en minusculas.</p>
	 */
	public static String nombre(String texto) {
		String base = compactar(texto);
		if (base == null) {
			return null;
		}
		return mayusculaInicial(ajustarCaja(base));
	}

	/**
	 * Normaliza un enunciado o descripcion.
	 *
	 * <p>Ademas de lo anterior, se asegura de que termine en punto: un enunciado
	 * es una oracion, y la mitad con punto y la mitad sin el se lee como
	 * descuido.</p>
	 */
	public static String enunciado(String texto) {
		String base = nombre(texto);
		if (base == null) {
			return null;
		}
		return base.endsWith(".") || base.endsWith(":") || base.endsWith("?")
				? base
				: base + ".";
	}

	/** Recorta y colapsa los espacios. Devuelve nulo si no queda nada. */
	private static String compactar(String texto) {
		if (texto == null) {
			return null;
		}
		String base = texto.trim().replaceAll("\\s+", " ");
		return base.isEmpty() ? null : base;
	}

	/**
	 * Ajusta la caja cuando el texto viene entero en mayusculas o en minusculas.
	 *
	 * <p>Un texto con mayusculas y minusculas mezcladas se respeta: quien lo
	 * escribio decidio donde iban, y ahi puede haber siglas o nombres propios que
	 * este metodo no sabria distinguir.</p>
	 */
	private static String ajustarCaja(String texto) {
		boolean tieneMinuscula = texto.chars().anyMatch(Character::isLowerCase);
		boolean tieneMayuscula = texto.chars().anyMatch(Character::isUpperCase);

		// Entero en mayusculas: se baja todo, salvo las palabras de dos o tres
		// letras enteramente en mayusculas, que casi siempre son siglas.
		if (!tieneMinuscula && tieneMayuscula) {
			StringBuilder salida = new StringBuilder();
			for (String palabra : texto.split(" ")) {
				if (salida.length() > 0) {
					salida.append(' ');
				}
				salida.append(esSigla(palabra) ? palabra : palabra.toLowerCase(Locale.ROOT));
			}
			return salida.toString();
		}
		return texto;
	}

	/**
	 * Palabras cortas que en un texto en mayusculas no son siglas.
	 *
	 * <p>Sin esta lista, "EL" y "EN" pasarian por siglas por tener dos letras
	 * mayusculas, y el texto quedaria como "Exportar EL historial EN CSV".</p>
	 */
	private static final Set<String> NO_SON_SIGLAS = Set.of(
			"EL", "LA", "LO", "LOS", "LAS", "UN", "UNA", "DE", "DEL", "AL", "EN", "CON",
			"POR", "SIN", "SU", "SUS", "QUE", "SE", "ES", "NO", "YA", "SI", "Y", "O", "A");

	private static boolean esSigla(String palabra) {
		String limpia = palabra.replaceAll("[^\\p{L}]", "");

		return limpia.length() >= 2 && limpia.length() <= 5
				&& limpia.chars().allMatch(Character::isUpperCase)
				&& !NO_SON_SIGLAS.contains(limpia);
	}

	private static String mayusculaInicial(String texto) {
		for (int i = 0; i < texto.length(); i++) {
			if (Character.isLetter(texto.charAt(i))) {
				return texto.substring(0, i)
						+ Character.toUpperCase(texto.charAt(i))
						+ texto.substring(i + 1);
			}
		}
		return texto;
	}
}
