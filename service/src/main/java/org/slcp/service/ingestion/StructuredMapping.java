package org.slcp.service.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte un objeto ya analizado en campos canonicos.
 *
 * <p>Lo comparten los lectores de JSON y de YAML: una vez analizado, un
 * documento de cualquiera de los dos es lo mismo --- listas y correspondencias
 * de nombre a valor --- y tratarlos por separado solo garantizaria que acabaran
 * comportandose distinto.</p>
 */
final class StructuredMapping {

	private StructuredMapping() {
	}

	/**
	 * Anota los pares de un objeto como campos.
	 *
	 * @param desconocidas recibe las claves que el perfil no reconoce
	 */
	static void mapear(ImportProfile perfil, Map<?, ?> objeto,
			Map<String, String> campos, List<String> desconocidas) {

		for (Map.Entry<?, ?> entrada : objeto.entrySet()) {
			String etiqueta = String.valueOf(entrada.getKey());
			Object bruto = entrada.getValue();

			// Un objeto anidado se reporta como no reconocido aunque el perfil tenga
			// una correspondencia para su clave: lo que no se puede representar debe
			// constar, no desaparecer. Descartarlo en silencio dejaria a quien revisa
			// creyendo que el documento no traia ese dato.
			if (bruto instanceof Map<?, ?>) {
				desconocidas.add(etiqueta);
				continue;
			}

			String valor = comoTexto(bruto);
			if (valor.isEmpty()) {
				continue;
			}

			String campo = perfil.campoDe(etiqueta);
			if (campo.isEmpty()) {
				desconocidas.add(etiqueta);
			} else {
				campos.merge(campo, valor, (a, n) -> a.equals(n) ? a : a + " " + n);
			}
		}
	}

	/**
	 * Reduce un valor a texto.
	 *
	 * <p>Una lista de valores simples se une por comas, que es como se leeria. Un
	 * objeto anidado no se aplana: se descarta y su clave se reporta como no
	 * reconocida, porque adivinar como convertirlo en una linea de texto produciria
	 * un valor que nadie escribio.</p>
	 */
	static String comoTexto(Object valor) {
		if (valor == null) {
			return "";
		}
		if (valor instanceof Map<?, ?>) {
			return "";
		}
		if (valor instanceof List<?> lista) {
			List<String> partes = new ArrayList<>();
			for (Object elemento : lista) {
				if (elemento instanceof Map<?, ?> || elemento instanceof List<?>) {
					continue;
				}
				partes.add(String.valueOf(elemento).trim());
			}
			return String.join(", ", partes);
		}
		return String.valueOf(valor).trim();
	}

	/** Campos vacios listos para rellenar. */
	static Map<String, String> nuevosCampos() {
		return new LinkedHashMap<>();
	}
}
