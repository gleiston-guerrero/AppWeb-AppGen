package org.slcp.service.ingestion;

import java.io.IOException;
import java.io.Reader;

/**
 * Lector de un documento de requisitos.
 *
 * <p>Existe mas de una implementacion porque los formatos no se dejan leer
 * todos igual. Un documento de texto se recorre linea a linea; uno estructurado
 * hay que analizarlo con su propio analizador, porque su significado no depende
 * de como este repartido en lineas. Forzar el lector de lineas sobre un JSON
 * funcionaria con los archivos bien sangrados y fallaria con los compactos, que
 * es el peor modo de fallo posible: el que aparece mas tarde.</p>
 */
public interface RequirementSource {

	ExtractionReport extraer(Reader documento) throws IOException;

	/** Devuelve el lector que corresponde al perfil. */
	static RequirementSource of(ImportProfile perfil) {
		return switch (perfil.getReader()) {
			case "json" -> new JsonRequirementSource(perfil);
			case "yaml" -> new YamlRequirementSource(perfil);
			case "xml" -> new XmlRequirementSource(perfil);
			case "csv" -> new CsvRequirementSource(perfil);
			default -> new RequirementExtractor(perfil);
		};
	}
}
