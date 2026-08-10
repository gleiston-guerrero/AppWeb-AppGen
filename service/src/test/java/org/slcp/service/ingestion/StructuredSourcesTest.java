package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de los lectores estructurados.
 *
 * <p>Cada prueba ataca el caso en que un lector de lineas fracasaria. Comprobar
 * solo el ejemplo bien formateado no demostraria nada: es precisamente el caso
 * que ambos enfoques resuelven igual.</p>
 */
class StructuredSourcesTest {

	private ImportProfile perfil(String directivas) throws IOException {
		return ImportProfile.cargar(new StringReader(directivas));
	}

	// =================================================================
	// JSON
	// =================================================================

	private static final String PERFIL_JSON = """
			profile.id     = json
			profile.reader = json
			json.array     = requisitos
			field.id          = id
			field.descripcion = description
			field.criterio    = verification
			field.entradas    = inputs
			expected = id, description, verification
			""";

	@Test
	@DisplayName("JSON compacto en una sola linea se lee igual que formateado")
	void jsonCompacto() throws IOException {
		String compacto = "{\"requisitos\":[{\"id\":\"RF-01\",\"descripcion\":\"El sistema debera "
				+ "registrar.\",\"criterio\":\"Registrar y comprobar.\"}]}";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_JSON))
				.extraer(new StringReader(compacto));

		assertThat(informe.total()).isEqualTo(1);
		assertThat(informe.completos()).isEqualTo(1);
		assertThat(informe.requirements().get(0).sourceId()).isEqualTo("RF-01");
	}

	@Test
	@DisplayName("JSON: una lista de valores simples se une en una linea")
	void jsonListaDeValores() throws IOException {
		String documento = """
				{"requisitos":[{"id":"RF-01","descripcion":"Algo.","criterio":"Comprobar.",
				  "entradas":["sensor","valor","fecha"]}]}
				""";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_JSON))
				.extraer(new StringReader(documento));

		assertThat(informe.requirements().get(0).get("inputs")).isEqualTo("sensor, valor, fecha");
	}

	@Test
	@DisplayName("JSON: un objeto anidado no se aplana, se reporta como no reconocido")
	void jsonObjetoAnidado() throws IOException {
		String documento = """
				{"requisitos":[{"id":"RF-01","descripcion":"Algo.","criterio":"Comprobar.",
				  "trazabilidad":{"origen":"EV-01","nivel":2}}]}
				""";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_JSON))
				.extraer(new StringReader(documento));

		assertThat(informe.unknownLabels()).contains("trazabilidad");
		assertThat(informe.requirements().get(0).completo()).isTrue();
	}

	@Test
	@DisplayName("JSON: la lista tambien puede estar en la raiz")
	void jsonRaizLista() throws IOException {
		String documento = "[{\"id\":\"RF-01\",\"descripcion\":\"Algo.\",\"criterio\":\"Comprobar.\"}]";

		assertThat(RequirementSource.of(perfil(PERFIL_JSON))
				.extraer(new StringReader(documento)).total()).isEqualTo(1);
	}

	@Test
	@DisplayName("JSON invalido falla al leerse, en lugar de producir requisitos a medias")
	void jsonInvalido() throws IOException {
		ImportProfile p = perfil(PERFIL_JSON);

		assertThatThrownBy(() -> RequirementSource.of(p).extraer(new StringReader("{esto no es json")))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("no se pudo analizar");
	}

	// =================================================================
	// YAML
	// =================================================================

	private static final String PERFIL_YAML = """
			profile.id     = yaml
			profile.reader = yaml
			yaml.list      = requisitos
			field.id          = id
			field.descripcion = description
			field.criterio    = verification
			expected = id, description, verification
			""";

	@Test
	@DisplayName("YAML: lista de requisitos con campos anidados")
	void yamlBasico() throws IOException {
		String documento = """
				requisitos:
				  - id: RF-01
				    descripcion: El sistema debera registrar la parcela.
				    criterio: Registrar y comprobar.
				  - id: RF-02
				    descripcion: El sistema debera almacenar las lecturas.
				    criterio: Enviar una lectura y comprobar.
				""";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_YAML))
				.extraer(new StringReader(documento));

		assertThat(informe.total()).isEqualTo(2);
		assertThat(informe.completos()).isEqualTo(2);
	}

	@Test
	@DisplayName("YAML: un enunciado en varias lineas llega entero")
	void yamlTextoLargo() throws IOException {
		String documento = """
				requisitos:
				  - id: RF-01
				    descripcion: >
				      El sistema debera registrar la parcela con su superficie,
				      su tipo de suelo y su cultivo sembrado.
				    criterio: Registrar y comprobar.
				""";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_YAML))
				.extraer(new StringReader(documento));

		String enunciado = informe.requirements().get(0).get("description");
		assertThat(enunciado).contains("superficie");
		assertThat(enunciado).contains("cultivo sembrado");
	}

	// =================================================================
	// XML
	// =================================================================

	private static final String PERFIL_XML = """
			profile.id     = xml
			profile.reader = xml
			xml.item       = requisito
			field.id          = id
			field.descripcion = description
			field.criterio    = verification
			field.prioridad   = priority
			expected = id, description, verification
			""";

	@Test
	@DisplayName("XML: los campos pueden ser atributos o elementos hijos")
	void xmlAtributosYElementos() throws IOException {
		String documento = """
				<especificacion>
				  <requisito id="RF-01" prioridad="Must">
				    <descripcion>El sistema debera registrar la parcela.</descripcion>
				    <criterio>Registrar y comprobar.</criterio>
				  </requisito>
				</especificacion>
				""";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_XML))
				.extraer(new StringReader(documento));

		assertThat(informe.total()).isEqualTo(1);
		assertThat(informe.requirements().get(0).sourceId()).isEqualTo("RF-01");
		assertThat(informe.requirements().get(0).get("priority")).isEqualTo("Must");
		assertThat(informe.completos()).isEqualTo(1);
	}

	@Test
	@DisplayName("XML: una declaracion de tipo de documento se rechaza")
	void xmlSinEntidadesExternas() throws IOException {
		String peligroso = """
				<?xml version="1.0"?>
				<!DOCTYPE especificacion [<!ENTITY x SYSTEM "file:///etc/passwd">]>
				<especificacion><requisito id="RF-01"><descripcion>&x;</descripcion></requisito></especificacion>
				""";

		ImportProfile p = perfil(PERFIL_XML);
		assertThatThrownBy(() -> RequirementSource.of(p).extraer(new StringReader(peligroso)))
				.isInstanceOf(IOException.class);
	}

	// =================================================================
	// CSV
	// =================================================================

	private static final String PERFIL_CSV = """
			profile.id     = csv
			profile.reader = csv
			csv.separator  = ,
			field.identificador = id
			field.descripcion   = description
			field.criterio      = verification
			expected = id, description, verification
			""";

	@Test
	@DisplayName("CSV: un campo entrecomillado puede contener comas")
	void csvComasDentroDelCampo() throws IOException {
		String documento = """
				Identificador,Descripcion,Criterio
				RF-01,"El sistema debera registrar la parcela con superficie, suelo y cultivo.",Registrar y comprobar.
				""";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_CSV))
				.extraer(new StringReader(documento));

		assertThat(informe.total()).isEqualTo(1);
		assertThat(informe.requirements().get(0).get("description"))
				.contains("superficie, suelo y cultivo");
	}

	@Test
	@DisplayName("CSV: un campo entrecomillado puede contener saltos de linea")
	void csvSaltosDentroDelCampo() throws IOException {
		String documento = "Identificador,Descripcion,Criterio\n"
				+ "RF-01,\"Primera linea\nSegunda linea\",Comprobar.\n";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_CSV))
				.extraer(new StringReader(documento));

		assertThat(informe.total()).isEqualTo(1);
		assertThat(informe.requirements().get(0).get("description")).contains("Segunda linea");
	}

	@Test
	@DisplayName("CSV: dos comillas seguidas representan una")
	void csvComillasEscapadas() throws IOException {
		String documento = "Identificador,Descripcion,Criterio\n"
				+ "RF-01,\"El estado \"\"activo\"\" debera constar.\",Comprobar.\n";

		ExtractionReport informe = RequirementSource.of(perfil(PERFIL_CSV))
				.extraer(new StringReader(documento));

		assertThat(informe.requirements().get(0).get("description")).contains("\"activo\"");
	}

	@Test
	@DisplayName("CSV: las filas vacias se ignoran")
	void csvFilasVacias() throws IOException {
		String documento = """
				Identificador,Descripcion,Criterio
				RF-01,Algo.,Comprobar.

				RF-02,Otra cosa.,Comprobar tambien.
				""";

		assertThat(RequirementSource.of(perfil(PERFIL_CSV))
				.extraer(new StringReader(documento)).total()).isEqualTo(2);
	}

	@Test
	@DisplayName("CSV: la posicion apunta a la fila del archivo, cabecera incluida")
	void csvPosicion() throws IOException {
		String documento = """
				Identificador,Descripcion,Criterio
				RF-01,Algo.,Comprobar.
				""";

		assertThat(RequirementSource.of(perfil(PERFIL_CSV))
				.extraer(new StringReader(documento))
				.requirements().get(0).sourceLine()).isEqualTo(2);
	}

	// =================================================================

	@Test
	@DisplayName("Todos los lectores detectan identificadores repetidos")
	void duplicadosEnTodos() throws IOException {
		String json = "[{\"id\":\"RF-01\",\"descripcion\":\"A.\",\"criterio\":\"C.\"},"
				+ "{\"id\":\"RF-01\",\"descripcion\":\"B.\",\"criterio\":\"C.\"}]";

		assertThat(RequirementSource.of(perfil(PERFIL_JSON))
				.extraer(new StringReader(json)).duplicateIds()).containsExactly("RF-01");
	}
}
