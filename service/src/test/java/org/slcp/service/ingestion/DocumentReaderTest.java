package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del lector de formatos con estructura propia. */
class DocumentReaderTest {

	private final ObjectMapper om = new ObjectMapper();
	private final DocumentReader lector = new DocumentReader(om);

	// =================================================================
	// Valores separados por comas
	// =================================================================

	@Test
	@DisplayName("CSV: una coma dentro de un valor entrecomillado no parte la fila")
	void comaDentroDeValor() {
		List<List<String>> filas = DocumentReader.analizarCsv(
				"id,texto\nRF-01,\"Registrar nombre, especie y raza\"\n");

		assertThat(filas).hasSize(2);
		assertThat(filas.get(1)).containsExactly("RF-01", "Registrar nombre, especie y raza");
	}

	@Test
	@DisplayName("CSV: dos comillas seguidas representan una comilla literal")
	void comillaEscapada() {
		List<List<String>> filas = DocumentReader.analizarCsv(
				"id,texto\nRF-01,\"Dijo \"\"listo\"\" y salio\"\n");

		assertThat(filas.get(1).get(1)).isEqualTo("Dijo \"listo\" y salio");
	}

	@Test
	@DisplayName("CSV: un salto de linea dentro de un valor no crea una fila nueva")
	void saltoDentroDeValor() {
		List<List<String>> filas = DocumentReader.analizarCsv(
				"id,texto\nRF-01,\"Primera linea\nSegunda linea\"\n");

		assertThat(filas).hasSize(2);
		assertThat(filas.get(1).get(1)).contains("Primera linea").contains("Segunda linea");
	}

	@Test
	@DisplayName("CSV: la ultima fila sin salto final no se pierde")
	void ultimaFilaSinSalto() {
		List<List<String>> filas = DocumentReader.analizarCsv("id,texto\nRF-01,Uno");

		assertThat(filas).hasSize(2);
		assertThat(filas.get(1)).containsExactly("RF-01", "Uno");
	}

	@Test
	@DisplayName("CSV: la cabecera da las claves de cada objeto")
	void csvAObjetos() throws IOException {
		JsonNode arbol = lector.leer("Identificador,Descripcion\nRF-01,Hace algo\n", "csv");

		assertThat(arbol.isArray()).isTrue();
		assertThat(arbol.get(0).get("Identificador").asText()).isEqualTo("RF-01");
	}

	// =================================================================
	// XML
	// =================================================================

	@Test
	@DisplayName("XML: atributos y elementos hijos se tratan igual")
	void atributosYElementos() throws IOException {
		JsonNode arbol = lector.leer("""
				<requirements>
				  <requirement id="RF-01" priority="Must">
				    <description>Hace algo</description>
				  </requirement>
				</requirements>
				""", "xml");

		assertThat(arbol.get(0).get("id").asText()).isEqualTo("RF-01");
		assertThat(arbol.get(0).get("priority").asText()).isEqualTo("Must");
		assertThat(arbol.get(0).get("description").asText()).isEqualTo("Hace algo");
	}

	@Test
	@DisplayName("XML: no se procesan entidades externas")
	void sinEntidadesExternas() {
		String malicioso = """
				<?xml version="1.0"?>
				<!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
				<requirements><requirement id="&x;"/></requirements>
				""";

		// Se rechaza el documento entero antes de resolver nada: un archivo subido
		// por cualquiera no debe poder hacer que el servidor lea sus propios
		// archivos ni abra conexiones de red.
		assertThatThrownBy(() -> lector.leer(malicioso, "xml")).isInstanceOf(IOException.class);
	}

	// =================================================================
	// JSON y YAML
	// =================================================================

	@Test
	@DisplayName("JSON: se lee el arreglo con sus claves")
	void jsonBasico() throws IOException {
		JsonNode arbol = lector.leer("""
				{"requirements":[{"id":"RF-01","description":"Hace algo"}]}
				""", "json");

		assertThat(arbol.get("requirements").get(0).get("id").asText()).isEqualTo("RF-01");
	}

	@Test
	@DisplayName("YAML: el texto de varias lineas llega entero")
	void yamlMultilinea() throws IOException {
		JsonNode arbol = lector.leer("""
				requirements:
				  - id: RF-01
				    description: >
				      Primera parte
				      y segunda parte.
				""", "yaml");

		String texto = arbol.get("requirements").get(0).get("description").asText();
		assertThat(texto).contains("Primera parte").contains("y segunda parte");
	}

	@Test
	@DisplayName("YAML: la carga es restringida a tipos simples")
	void yamlSinInstanciarClases() {
		// La carga general de YAML puede instanciar clases nombradas en el propio
		// documento, y aqui el documento lo sube cualquiera.
		assertThatThrownBy(() -> lector.leer(
				"!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[]]]", "yaml"))
				.isInstanceOf(Exception.class);
	}

	@Test
	@DisplayName("Un formato no reconocido se rechaza en lugar de intentarse")
	void formatoDesconocido() {
		assertThatThrownBy(() -> lector.leer("algo", "excel"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
