package org.slcp.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lector de JSON.
 *
 * <p>Se analiza como JSON y no linea a linea. Un documento compacto, sin saltos
 * ni sangrado, es tan valido como uno formateado, y un lector de lineas
 * funcionaria con el segundo y fallaria con el primero sin que la diferencia
 * fuese visible al escribir el perfil.</p>
 */
public final class JsonRequirementSource implements RequirementSource {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ImportProfile perfil;

	public JsonRequirementSource(ImportProfile perfil) {
		this.perfil = perfil;
	}

	@Override
	@SuppressWarnings("unchecked")
	public ExtractionReport extraer(Reader documento) throws IOException {
		ExtractionBuilder constructor = new ExtractionBuilder(perfil);
		String propiedad = perfil.ajuste("json.array", "");

		Object raiz;
		try {
			raiz = MAPPER.readValue(documento, Object.class);
		} catch (IOException e) {
			throw new IOException("El documento JSON no se pudo analizar: " + e.getMessage(), e);
		}

		List<Object> elementos = new ArrayList<>();

		if (raiz instanceof List<?> lista) {
			elementos.addAll(lista);
		} else if (raiz instanceof Map<?, ?> objeto) {
			Object contenido = propiedad.isEmpty() ? null : objeto.get(propiedad);
			if (contenido instanceof List<?> lista) {
				elementos.addAll(lista);
			} else {
				// Un objeto suelto es un requisito unico, que es lo que se recibe al
				// exportar de uno en uno.
				elementos.add(objeto);
			}
		}

		int posicion = 0;
		for (Object elemento : elementos) {
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
}
