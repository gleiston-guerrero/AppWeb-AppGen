package org.slcp.service.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector de valores separados por comas.
 *
 * <p>Su modelo es traspuesto respecto del de un documento de texto: alli cada
 * requisito ocupa un bloque y cada campo una linea; aqui cada requisito ocupa
 * una fila y cada campo una columna, y las etiquetas van una sola vez en la
 * cabecera.</p>
 *
 * <p>Se analiza a mano y no partiendo por comas: un campo entrecomillado puede
 * contener comas, saltos de linea y comillas escapadas, y partir sin mas
 * destrozaria justamente los enunciados largos, que son casi todos.</p>
 */
public final class CsvRequirementSource implements RequirementSource {

	private final ImportProfile perfil;

	public CsvRequirementSource(ImportProfile perfil) {
		this.perfil = perfil;
	}

	@Override
	public ExtractionReport extraer(Reader documento) throws IOException {
		ExtractionBuilder constructor = new ExtractionBuilder(perfil);
		char separador = perfil.ajuste("csv.separator", ",").charAt(0);

		String texto;
		try (BufferedReader lector = new BufferedReader(documento)) {
			StringBuilder sb = new StringBuilder();
			int c;
			while ((c = lector.read()) != -1) {
				sb.append((char) c);
			}
			texto = sb.toString();
		}

		List<List<String>> filas = analizar(texto, separador);
		if (filas.isEmpty()) {
			return constructor.construir();
		}

		List<String> cabecera = filas.get(0);

		for (int f = 1; f < filas.size(); f++) {
			List<String> fila = filas.get(f);
			if (fila.stream().allMatch(String::isBlank)) {
				continue;
			}

			Map<String, String> campos = new LinkedHashMap<>();
			List<String> desconocidas = new ArrayList<>();

			for (int c = 0; c < cabecera.size(); c++) {
				String etiqueta = cabecera.get(c).trim();
				if (etiqueta.isEmpty()) {
					continue;
				}
				String valor = c < fila.size() ? fila.get(c).trim() : "";
				if (valor.isEmpty()) {
					continue;
				}

				String campo = perfil.campoDe(etiqueta);
				if (campo.isEmpty()) {
					desconocidas.add(etiqueta);
				} else {
					campos.merge(campo, valor,
							(a, n) -> a.equals(n) ? a : a + " " + n);
				}
			}

			// La fila 1 del archivo es la cabecera, de modo que la primera de datos
			// es la 2: quien vaya a corregir el archivo busca ese numero.
			constructor.anadir(campos, desconocidas, f + 1);
		}

		return constructor.construir();
	}

	/**
	 * Analiza el texto completo en filas y columnas.
	 *
	 * <p>Respeta el entrecomillado: dentro de comillas, el separador y el salto de
	 * linea son parte del valor, y dos comillas seguidas representan una.</p>
	 */
	private List<List<String>> analizar(String texto, char separador) {
		List<List<String>> filas = new ArrayList<>();
		List<String> fila = new ArrayList<>();
		StringBuilder campo = new StringBuilder();
		boolean entreComillas = false;

		for (int i = 0; i < texto.length(); i++) {
			char c = texto.charAt(i);

			if (entreComillas) {
				if (c == '"') {
					if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
						campo.append('"');
						i++;
					} else {
						entreComillas = false;
					}
				} else {
					campo.append(c);
				}
				continue;
			}

			if (c == '"') {
				entreComillas = true;
			} else if (c == separador) {
				fila.add(campo.toString());
				campo.setLength(0);
			} else if (c == '\n') {
				fila.add(campo.toString());
				campo.setLength(0);
				filas.add(fila);
				fila = new ArrayList<>();
			} else if (c != '\r') {
				campo.append(c);
			}
		}

		if (campo.length() > 0 || !fila.isEmpty()) {
			fila.add(campo.toString());
			filas.add(fila);
		}
		return filas;
	}
}
