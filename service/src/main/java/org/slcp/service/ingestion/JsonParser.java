package org.slcp.service.ingestion;

/**
 * Analizador de JSON conforme a RFC 8259, reducido a lo que hace falta.
 *
 * <p>Se escribe aqui en lugar de emplear una biblioteca porque el armazon cambio
 * de version de la suya y con ello el paquete de sus clases, de modo que el
 * codigo atado a una version deja de compilar al actualizar. Un analizador de
 * doscientas lineas que solo necesita el lenguaje no tiene ese problema.</p>
 *
 * <p>Los numeros y los valores logicos se conservan como texto: un requisito no
 * los necesita como tales, y tratarlos como texto evita que la representacion de
 * un decimal cambie al pasar por el analizador.</p>
 *
 * <p>Devuelve colecciones del lenguaje --- {@code Map}, {@code List} y
 * {@code String} --- que es exactamente lo que produce el lector de YAML, de
 * modo que ambos comparten la conversion a campos.</p>
 */
public final class JsonParser {

	/** El documento no es JSON valido. */
	public static class JsonParseException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public JsonParseException(String mensaje, int posicion) {
			super(mensaje + " (posicion " + posicion + ")");
		}
	}

	/**
	 * Profundidad maxima de anidamiento.
	 *
	 * <p>Un documento con miles de corchetes anidados agotaria la pila y detendria
	 * el servicio. Como el documento lo sube cualquiera, el limite es una medida
	 * de seguridad y no una comodidad.</p>
	 */
	private static final int PROFUNDIDAD_MAXIMA = 64;

	private final String texto;
	private int i;

	private JsonParser(String texto) {
		this.texto = texto;
	}

	/** Analiza el documento completo. */
	public static Object analizar(String contenido) {
		if (contenido == null || contenido.isBlank()) {
			throw new JsonParseException("El documento esta vacio", 0);
		}
		JsonParser p = new JsonParser(contenido);
		p.saltarEspacio();
		Object raiz = p.valor(0);
		p.saltarEspacio();
		if (p.i < p.texto.length()) {
			throw new JsonParseException("Sobra contenido despues del documento", p.i);
		}
		return raiz;
	}

	// =================================================================

	private Object valor(int profundidad) {
		if (profundidad > PROFUNDIDAD_MAXIMA) {
			throw new JsonParseException("El documento anida demasiado", i);
		}
		saltarEspacio();
		if (i >= texto.length()) {
			throw new JsonParseException("Se esperaba un valor y el documento termino", i);
		}

		char c = texto.charAt(i);
		return switch (c) {
			case '{' -> objeto(profundidad);
			case '[' -> lista(profundidad);
			case '"' -> cadena();
			case 't', 'f' -> literal(c == 't' ? "true" : "false");
			case 'n' -> {
				literal("null");
				yield null;
			}
			default -> numero();
		};
	}

	private Object objeto(int profundidad) {
		java.util.Map<String, Object> objeto = new java.util.LinkedHashMap<>();
		i++;
		saltarEspacio();

		if (consumir('}')) {
			return objeto;
		}

		while (true) {
			saltarEspacio();
			if (i >= texto.length() || texto.charAt(i) != '"') {
				throw new JsonParseException("Se esperaba el nombre de un campo", i);
			}
			String clave = cadena();

			saltarEspacio();
			if (!consumir(':')) {
				throw new JsonParseException("Se esperaba ':' tras el nombre del campo", i);
			}

			objeto.put(clave, valor(profundidad + 1));
			saltarEspacio();

			if (consumir(',')) {
				continue;
			}
			if (consumir('}')) {
				return objeto;
			}
			throw new JsonParseException("Se esperaba ',' o '}'", i);
		}
	}

	private Object lista(int profundidad) {
		java.util.List<Object> lista = new java.util.ArrayList<>();
		i++;
		saltarEspacio();

		if (consumir(']')) {
			return lista;
		}

		while (true) {
			lista.add(valor(profundidad + 1));
			saltarEspacio();

			if (consumir(',')) {
				continue;
			}
			if (consumir(']')) {
				return lista;
			}
			throw new JsonParseException("Se esperaba ',' o ']'", i);
		}
	}

	private String cadena() {
		i++;
		StringBuilder salida = new StringBuilder();

		while (i < texto.length()) {
			char c = texto.charAt(i);

			if (c == '"') {
				i++;
				return salida.toString();
			}

			if (c == '\\') {
				i++;
				if (i >= texto.length()) {
					throw new JsonParseException("Escape incompleto", i);
				}
				char e = texto.charAt(i);
				switch (e) {
					case '"' -> salida.append('"');
					case '\\' -> salida.append('\\');
					case '/' -> salida.append('/');
					case 'b' -> salida.append('\b');
					case 'f' -> salida.append('\f');
					case 'n' -> salida.append('\n');
					case 'r' -> salida.append('\r');
					case 't' -> salida.append('\t');
					case 'u' -> {
						if (i + 4 >= texto.length()) {
							throw new JsonParseException("Escape unicode incompleto", i);
						}
						String hex = texto.substring(i + 1, i + 5);
						try {
							salida.append((char) Integer.parseInt(hex, 16));
						} catch (NumberFormatException ex) {
							throw new JsonParseException("Escape unicode no valido: " + hex, i);
						}
						i += 4;
					}
					default -> throw new JsonParseException("Escape no reconocido: \\" + e, i);
				}
				i++;
				continue;
			}

			salida.append(c);
			i++;
		}
		throw new JsonParseException("Cadena sin cerrar", i);
	}

	private String numero() {
		int inicio = i;
		if (i < texto.length() && (texto.charAt(i) == '-' || texto.charAt(i) == '+')) {
			i++;
		}
		while (i < texto.length() && (Character.isDigit(texto.charAt(i))
				|| texto.charAt(i) == '.' || texto.charAt(i) == 'e' || texto.charAt(i) == 'E'
				|| texto.charAt(i) == '-' || texto.charAt(i) == '+')) {
			i++;
		}
		if (i == inicio) {
			throw new JsonParseException("Se esperaba un valor y se encontro '"
					+ texto.charAt(i) + "'", i);
		}
		return texto.substring(inicio, i);
	}

	private String literal(String esperado) {
		if (!texto.startsWith(esperado, i)) {
			throw new JsonParseException("Se esperaba " + esperado, i);
		}
		i += esperado.length();
		return esperado;
	}

	private boolean consumir(char c) {
		saltarEspacio();
		if (i < texto.length() && texto.charAt(i) == c) {
			i++;
			return true;
		}
		return false;
	}

	private void saltarEspacio() {
		while (i < texto.length() && Character.isWhitespace(texto.charAt(i))) {
			i++;
		}
	}
}
