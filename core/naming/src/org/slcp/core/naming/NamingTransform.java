package org.slcp.core.naming;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Funcion unica y determinista de transformacion de nomenclatura.
 *
 * <p>Realiza los requisitos NAM-04 (la conversion entre la forma canonica y la
 * forma de cada destino la realiza una funcion unica de la plataforma, nunca la
 * plantilla individual), NAM-05 (prohibicion de colision con palabra reservada),
 * NAM-06 (limite de longitud portable con abreviatura determinista) y NAM-07
 * (la forma plural procede del glosario del proyecto).</p>
 *
 * <p>Nivel de aplicacion: [N1] la clase pertenece al nucleo de la plataforma;
 * [N3] su salida son los identificadores de la aplicacion generada. Conforme a
 * NAM-01, los terminos de dominio conservan el idioma del glosario y solo se
 * transliteran a ASCII para ser validos como identificadores; en ningun caso se
 * traducen.</p>
 *
 * <p>La clase no tiene estado ni dependencias externas: la misma entrada produce
 * siempre la misma salida, lo que la situa en el regimen determinista declarado
 * por TRC-23.</p>
 */
public final class NamingTransform {

	/** Longitud de la firma hexadecimal que se anexa al abreviar (NAM-06). */
	private static final int HASH_LENGTH = 8;

	private NamingTransform() {
	}

	/**
	 * Transforma un termino canonico en el identificador correspondiente al
	 * destino y a la clase solicitados, aplicando el limite de longitud por
	 * defecto del destino.
	 *
	 * @param singular forma singular canonica tomada del glosario
	 * @param plural   forma plural canonica tomada del glosario; puede ser nula
	 *                 salvo para las clases que la requieren
	 * @param target   destino de generacion
	 * @param kind     clase de identificador
	 * @return identificador listo para insertarse en el artefacto generado
	 * @throws NamingException si el termino es invalido, falta la forma plural
	 *                         requerida o el resultado colisiona con una palabra
	 *                         reservada del destino
	 */
	public static String render(String singular, String plural, Target target, Kind kind) {
		return render(singular, plural, target, kind, target.getDefaultMaxLength());
	}

	/**
	 * Variante que permite imponer un limite de longitud distinto del propio del
	 * destino. Es la forma que debe usar el proyecto cuando tiene habilitados
	 * varios gestores de bases de datos, porque NAM-06 obliga a adoptar el limite
	 * mas restrictivo de todos ellos.
	 *
	 * @param maxLength limite de longitud; 0 o negativo desactiva la regla
	 */
	public static String render(String singular, String plural, Target target, Kind kind, int maxLength) {
		List<String> tokens = tokensFor(singular, plural, kind);
		String rendered = applyStyle(tokens, styleFor(target, kind));

		if (ReservedWords.isReserved(target, rendered)) {
			throw new NamingException(NamingException.Code.RESERVED_WORD,
					"el identificador '" + rendered + "' colisiona con una palabra reservada de "
							+ target + "; corrija el termino en el glosario del proyecto");
		}

		return applyLengthRule(rendered, maxLength);
	}

	/**
	 * Selecciona la forma singular o plural segun la clase de identificador y la
	 * descompone en unidades lexicas. La forma plural nunca se infiere: si la
	 * clase la requiere y el glosario no la aporta, la transformacion aborta
	 * (NAM-07). Inferirla seria incorrecto para cualquier idioma cuyo plural no
	 * sea regular, y silenciosamente incorrecto, que es peor.
	 */
	private static List<String> tokensFor(String singular, String plural, Kind kind) {
		if (isBlank(singular)) {
			throw new NamingException(NamingException.Code.EMPTY_TERM,
					"el termino canonico singular es obligatorio");
		}

		String source = singular;
		if (kind == Kind.TABLE || kind == Kind.PATH) {
			if (isBlank(plural)) {
				throw new NamingException(NamingException.Code.MISSING_PLURAL,
						"la clase " + kind + " requiere la forma plural, que debe declararse "
								+ "en el glosario del proyecto y nunca se infiere");
			}
			source = plural;
		}

		List<String> tokens = tokenize(source);
		if (tokens.isEmpty()) {
			throw new NamingException(NamingException.Code.EMPTY_TERM,
					"el termino '" + source + "' no contiene unidades lexicas utilizables");
		}
		if (!Character.isLetter(tokens.get(0).charAt(0))) {
			throw new NamingException(NamingException.Code.INVALID_TERM,
					"el termino '" + source + "' no comienza por letra y no puede producir "
							+ "un identificador valido");
		}
		return tokens;
	}

	/**
	 * Descompone un termino en unidades lexicas en minusculas, con independencia
	 * de la forma en que se haya escrito. Acepta UpperCamelCase, lowerCamelCase,
	 * snake_case, kebab-case, mayusculas y texto separado por espacios, de modo
	 * que un mismo concepto produce siempre los mismos tokens.
	 *
	 * <p>Los diacriticos se eliminan por transliteracion, no por traduccion: un
	 * termino de dominio en espanol conserva su idioma y solo pierde las marcas
	 * que ningun destino admite en un identificador sin entrecomillar.</p>
	 */
	static List<String> tokenize(String term) {
		String ascii = transliterate(term);
		StringBuilder spaced = new StringBuilder(ascii.length() * 2);

		for (int i = 0; i < ascii.length(); i++) {
			char c = ascii.charAt(i);
			if (c == '_' || c == '-' || Character.isWhitespace(c) || c == '.') {
				spaced.append(' ');
				continue;
			}
			boolean boundary = i > 0
					&& Character.isUpperCase(c)
					&& (Character.isLowerCase(ascii.charAt(i - 1)) || Character.isDigit(ascii.charAt(i - 1)));
			if (boundary) {
				spaced.append(' ');
			}
			spaced.append(c);
		}

		List<String> tokens = new ArrayList<>();
		for (String raw : spaced.toString().split(" +")) {
			String cleaned = raw.replaceAll("[^A-Za-z0-9]", "");
			if (!cleaned.isEmpty()) {
				tokens.add(cleaned.toLowerCase(Locale.ROOT));
			}
		}
		return tokens;
	}

	/** Elimina diacriticos conservando la letra base; la 'n' de 'ñ' se preserva. */
	private static String transliterate(String term) {
		String decomposed = Normalizer.normalize(term, Normalizer.Form.NFD);
		StringBuilder out = new StringBuilder(decomposed.length());
		for (int i = 0; i < decomposed.length(); i++) {
			char c = decomposed.charAt(i);
			if (Character.getType(c) != Character.NON_SPACING_MARK) {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** Estilos de composicion admitidos. */
	private enum Style {
		UPPER_CAMEL, LOWER_CAMEL, UPPER_SNAKE, LOWER_SNAKE, KEBAB
	}

	/**
	 * Tabla de correspondencia entre par destino-clase y estilo, tomada de la
	 * tabla de convenciones de nomenclatura de SLCP-DOC-001.
	 */
	private static Style styleFor(Target target, Kind kind) {
		if (kind == Kind.CONSTANT) {
			return Style.UPPER_SNAKE;
		}
		switch (target) {
			case JAVA:
			case PHP:
				return kind == Kind.TYPE ? Style.UPPER_CAMEL : Style.LOWER_CAMEL;
			case CSHARP:
				return Style.UPPER_CAMEL;
			case JSON:
				return Style.LOWER_CAMEL;
			case SQL_POSTGRES:
			case SQL_MYSQL:
			case SQL_MARIADB:
			case SQL_ORACLE:
				return Style.LOWER_SNAKE;
			case REST:
			case FILE:
			default:
				return Style.KEBAB;
		}
	}

	private static String applyStyle(List<String> tokens, Style style) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < tokens.size(); i++) {
			String t = tokens.get(i);
			switch (style) {
				case UPPER_CAMEL:
					out.append(capitalize(t));
					break;
				case LOWER_CAMEL:
					out.append(i == 0 ? t : capitalize(t));
					break;
				case UPPER_SNAKE:
					if (i > 0) {
						out.append('_');
					}
					out.append(t.toUpperCase(Locale.ROOT));
					break;
				case LOWER_SNAKE:
					if (i > 0) {
						out.append('_');
					}
					out.append(t);
					break;
				case KEBAB:
				default:
					if (i > 0) {
						out.append('-');
					}
					out.append(t);
					break;
			}
		}
		return out.toString();
	}

	/**
	 * Aplica la regla de longitud de NAM-06. Cuando el identificador excede el
	 * limite se conserva un prefijo legible y se anexa una firma derivada del
	 * identificador completo, de modo que la abreviatura es determinista y no
	 * puede producir colisiones entre terminos distintos. La correspondencia
	 * entre forma abreviada y forma completa debe quedar registrada en el
	 * glosario del proyecto, que es lo que la hace reversible por consulta.
	 */
	static String applyLengthRule(String rendered, int maxLength) {
		if (maxLength <= 0 || rendered.length() <= maxLength) {
			return rendered;
		}
		String signature = signature(rendered);
		int prefixLength = maxLength - (HASH_LENGTH + 1);
		if (prefixLength < 1) {
			return signature;
		}
		String prefix = rendered.substring(0, prefixLength);
		prefix = stripTrailingSeparators(prefix);
		return prefix + "_" + signature;
	}

	private static String stripTrailingSeparators(String s) {
		int end = s.length();
		while (end > 0 && (s.charAt(end - 1) == '_' || s.charAt(end - 1) == '-')) {
			end--;
		}
		return s.substring(0, end);
	}

	/** Primeros digitos hexadecimales del SHA-256 del identificador completo. */
	private static String signature(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < HASH_LENGTH / 2; i++) {
				hex.append(String.format("%02x", bytes[i]));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 debe estar disponible en toda JVM conforme", e);
		}
	}

	private static String capitalize(String token) {
		if (token.isEmpty()) {
			return token;
		}
		return Character.toUpperCase(token.charAt(0)) + token.substring(1);
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
