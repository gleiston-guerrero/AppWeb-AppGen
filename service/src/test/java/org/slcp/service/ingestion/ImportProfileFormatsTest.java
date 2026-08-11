package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Oraculo de los formatos instalados.
 *
 * <p>Cada perfil se comprueba contra su propio ejemplo. Es la prueba que impide
 * el fallo mas probable de este diseno: que el ejemplo mostrado a quien va a
 * subir un archivo no corresponda con lo que el lector espera de verdad.</p>
 */
class ImportProfileFormatsTest {

	private ImportProfile cargar(String id) throws IOException {
		try (Reader r = new InputStreamReader(
				getClass().getResourceAsStream("/profiles/" + id + ".profile"),
				StandardCharsets.UTF_8)) {
			return ImportProfile.cargar(r);
		}
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "latex-atributos", "markdown-campos", "texto-etiquetado",
			"json-requisitos", "yaml-requisitos", "xml-requisitos", "csv-requisitos" })
	@DisplayName("Cada formato declara nombre, descripcion, extensiones y ejemplo")
	void metadatosCompletos(String id) throws IOException {
		ImportProfile p = cargar(id);

		assertThat(p.getName()).isNotBlank();
		assertThat(p.getDescription()).isNotBlank();
		assertThat(p.getExtensions()).isNotEmpty();
		assertThat(p.getExample()).isNotBlank();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "latex-atributos", "markdown-campos", "texto-etiquetado",
			"json-requisitos", "yaml-requisitos", "xml-requisitos", "csv-requisitos" })
	@DisplayName("El ejemplo de cada formato se lee con su propio perfil")
	void ejemploCoherente(String id) throws IOException {
		ImportProfile p = cargar(id);

		ExtractionReport informe = RequirementSource.of(p)
				.extraer(new StringReader(p.getExample()));

		assertThat(informe.total()).isPositive();
		assertThat(informe.unknownLabels()).isEmpty();
		assertThat(informe.incompletos()).isZero();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "latex-atributos", "markdown-campos", "texto-etiquetado",
			"json-requisitos", "yaml-requisitos", "xml-requisitos", "csv-requisitos" })
	@DisplayName("Las extensiones declaradas empiezan por punto")
	void extensionesBienFormadas(String id) throws IOException {
		for (String extension : cargar(id).getExtensions()) {
			assertThat(extension).startsWith(".");
		}
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "latex-atributos", "markdown-campos", "texto-etiquetado",
			"json-requisitos", "yaml-requisitos", "xml-requisitos", "csv-requisitos" })
	@DisplayName("Todo campo exigido esta entre los que el perfil reconoce")
	void exigidosReconocibles(String id) throws IOException {
		ImportProfile p = cargar(id);
		List<String> reconocidos = p.getFieldMap().values().stream().distinct().toList();

		assertThat(reconocidos).containsAll(p.getExpected());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "markdown-campos", "texto-etiquetado", "json-requisitos",
			"yaml-requisitos", "xml-requisitos", "csv-requisitos" })
	@DisplayName("Se extrae mas de un requisito seguido, sin confundir donde acaba cada uno")
	void bloquesConsecutivos(String id) throws IOException {
		ImportProfile p = cargar(id);

		ExtractionReport informe = RequirementSource.of(p)
				.extraer(new StringReader(p.getExample()));

		assertThat(informe.total()).isGreaterThanOrEqualTo(2);
		assertThat(informe.duplicateIds()).isEmpty();
	}
}
