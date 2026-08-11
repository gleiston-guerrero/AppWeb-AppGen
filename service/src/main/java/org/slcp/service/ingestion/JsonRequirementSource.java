package org.slcp.service.ingestion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lector de JSON.
 *
 * <p>Emplea el analizador propio de {@link JsonParser} y no una biblioteca de
 * serializacion. El armazon cambio de Jackson 2 a Jackson 3 y con ello el
 * paquete de sus clases; un codigo atado a una version deja de compilar al
 * actualizar, y un analizador que solo necesita el lenguaje no tiene ese
 * problema.</p>
 *
 * <p>Comparte con el lector de YAML la conversion a campos: una vez analizado,
 * un documento de cualquiera de los dos es lo mismo.</p>
 */
public final class JsonRequirementSource implements RequirementSource {

	private final ImportProfile perfil;

	public JsonRequirementSource(ImportProfile perfil) {
		this.perfil = perfil;
	}

	@Override
	public ExtractionReport extraer(Reader documento) throws IOException {
		ExtractionBuilder constructor = new ExtractionBuilder(perfil);
		String propiedad = perfil.ajuste("json.list", perfil.ajuste("list.path", ""));

		Object raiz;
		try (BufferedReader lector = new BufferedReader(documento)) {
			String contenido = lector.lines().collect(Collectors.joining("\n"));
			raiz = JsonParser.analizar(contenido);
		} catch (JsonParser.JsonParseException e) {
			throw new IOException("El documento JSON no se pudo analizar: " + e.getMessage(), e);
		}

		int posicion = 0;
		for (Object elemento : elementosDe(raiz, propiedad)) {
			posicion++;
			if (!(elemento instanceof Map<?, ?> objeto)) {
				continue;
			}
			Map<String, String> campos = StructuredMapping.nuevosCampos();
			List<String> desconocidas = new ArrayList<>();
			StructuredMapping.mapear(perfil, objeto, campos, desconocidas);
			constructor.anadir(campos, desconocidas, posicion);
		}

		return constructor.construir();
	}

	/**
	 * Localiza la lista de requisitos.
	 *
	 * <p>Se admite tanto una lista en la raiz como un objeto que la contenga bajo
	 * la clave declarada. Exigir una sola forma obligaria a reescribir documentos
	 * que ya existen solo por como envuelven sus datos.</p>
	 */
	private List<Object> elementosDe(Object raiz, String propiedad) {
		List<Object> elementos = new ArrayList<>();

		if (raiz instanceof List<?> lista) {
			elementos.addAll(lista);
			return elementos;
		}

		if (raiz instanceof Map<?, ?> objeto) {
			Object contenido = propiedad.isEmpty() ? null : objeto.get(propiedad);
			if (contenido instanceof List<?> lista) {
				elementos.addAll(lista);
				return elementos;
			}

			// Sin clave declarada, la primera lista de objetos que aparezca.
			for (Object valor : objeto.values()) {
				if (valor instanceof List<?> lista && !lista.isEmpty()
						&& lista.get(0) instanceof Map<?, ?>) {
					elementos.addAll(lista);
					return elementos;
				}
			}
			elementos.add(objeto);
		}
		return elementos;
	}
}
