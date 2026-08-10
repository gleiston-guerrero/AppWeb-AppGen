package org.slcp.service.ingestion;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Lector de YAML.
 *
 * <p>Se analiza con constructor seguro: YAML admite declarar tipos que el
 * analizador instanciarla al leer, y un documento de requisitos llega de fuera.
 * Sin esa restriccion, subir un archivo bastaria para ejecutar codigo.</p>
 */
public final class YamlRequirementSource implements RequirementSource {

	private final ImportProfile perfil;

	public YamlRequirementSource(ImportProfile perfil) {
		this.perfil = perfil;
	}

	@Override
	public ExtractionReport extraer(Reader documento) throws IOException {
		ExtractionBuilder constructor = new ExtractionBuilder(perfil);
		String propiedad = perfil.ajuste("yaml.list", "");

		Object raiz;
		try {
			raiz = new Yaml(new SafeConstructor(new LoaderOptions())).load(documento);
		} catch (RuntimeException e) {
			throw new IOException("El documento YAML no se pudo analizar: " + e.getMessage(), e);
		}

		List<Object> elementos = new ArrayList<>();

		if (raiz instanceof List<?> lista) {
			elementos.addAll(lista);
		} else if (raiz instanceof Map<?, ?> objeto) {
			Object contenido = propiedad.isEmpty() ? null : objeto.get(propiedad);
			if (contenido instanceof List<?> lista) {
				elementos.addAll(lista);
			} else {
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
