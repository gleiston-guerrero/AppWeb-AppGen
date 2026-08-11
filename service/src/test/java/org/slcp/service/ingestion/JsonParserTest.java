package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo del analizador de JSON propio.
 *
 * <p>Se escribio en lugar de emplear una biblioteca porque el armazon cambio de
 * Jackson 2 a Jackson 3, y con ello el paquete de sus clases. Como es codigo
 * propio, su correccion hay que demostrarla.</p>
 */
class JsonParserTest {

	@Test
	@DisplayName("Analiza un objeto con sus campos")
	void objetoSimple() {
		Object raiz = JsonParser.analizar("{\"id\":\"RF-01\",\"nombre\":\"Uno\"}");

		assertThat(raiz).isInstanceOf(Map.class);
		assertThat(((Map<?, ?>) raiz).get("id")).isEqualTo("RF-01");
	}

	@Test
	@DisplayName("Analiza una lista de objetos")
	void listaDeObjetos() {
		Object raiz = JsonParser.analizar("[{\"id\":\"RF-01\"},{\"id\":\"RF-02\"}]");

		assertThat(raiz).isInstanceOf(List.class);
		assertThat((List<?>) raiz).hasSize(2);
	}

	@Test
	@DisplayName("Los escapes de la cadena se resuelven")
	void escapes() {
		Object raiz = JsonParser.analizar(
				"{\"t\":\"Dijo \\\"listo\\\" y salio\\nsegunda linea\"}");

		String texto = String.valueOf(((Map<?, ?>) raiz).get("t"));
		assertThat(texto).contains("\"listo\"").contains("\n");
	}

	@Test
	@DisplayName("El escape unicode se resuelve al caracter que nombra")
	void escapeUnicode() {
		Object raiz = JsonParser.analizar("{\"t\":\"Descripci\\u00f3n\"}");

		assertThat(String.valueOf(((Map<?, ?>) raiz).get("t"))).isEqualTo("Descripci\u00f3n");
	}

	@Test
	@DisplayName("Un documento mal formado se rechaza en lugar de leerse a medias")
	void documentoMalFormado() {
		assertThatThrownBy(() -> JsonParser.analizar("{\"id\":\"RF-01\""))
				.isInstanceOf(JsonParser.JsonParseException.class);
		assertThatThrownBy(() -> JsonParser.analizar("{\"id\" \"RF-01\"}"))
				.isInstanceOf(JsonParser.JsonParseException.class);
		assertThatThrownBy(() -> JsonParser.analizar(""))
				.isInstanceOf(JsonParser.JsonParseException.class);
	}

	@Test
	@DisplayName("Sobra contenido tras el documento: se rechaza")
	void contenidoSobrante() {
		assertThatThrownBy(() -> JsonParser.analizar("{\"a\":\"b\"} sobra"))
				.isInstanceOf(JsonParser.JsonParseException.class);
	}

	@Test
	@DisplayName("Un anidamiento excesivo se rechaza en lugar de agotar la pila")
	void anidamientoExcesivo() {
		String profundo = "[".repeat(200) + "]".repeat(200);

		assertThatThrownBy(() -> JsonParser.analizar(profundo))
				.isInstanceOf(JsonParser.JsonParseException.class);
	}

	@Test
	@DisplayName("Los numeros se conservan tal como venian escritos")
	void numerosComoTexto() {
		Object raiz = JsonParser.analizar("{\"n\":1.50,\"e\":1e3}");

		assertThat(String.valueOf(((Map<?, ?>) raiz).get("n"))).isEqualTo("1.50");
		assertThat(String.valueOf(((Map<?, ?>) raiz).get("e"))).isEqualTo("1e3");
	}

	@Test
	@DisplayName("El lector de JSON extrae los requisitos de la clave declarada")
	void lecturaCompleta() throws Exception {
		ImportProfile p = ImportProfile.cargar(new StringReader("""
				profile.id     = prueba-json
				profile.reader = json
				json.list      = requirements
				field.id          = id
				field.description = description
				expected = id, description
				"""));

		ExtractionReport informe = RequirementSource.of(p).extraer(new StringReader("""
				{"requirements":[
				  {"id":"RF-01","description":"Hace algo"},
				  {"id":"RF-02","description":"Hace otra cosa"}
				]}
				"""));

		assertThat(informe.total()).isEqualTo(2);
		assertThat(informe.completos()).isEqualTo(2);
		assertThat(informe.requirements().get(1).get("description")).isEqualTo("Hace otra cosa");
	}

	@Test
	@DisplayName("Un JSON invalido produce un fallo con mensaje, no una lectura a medias")
	void jsonInvalidoEnElLector() throws Exception {
		ImportProfile p = ImportProfile.cargar(new StringReader("""
				profile.id     = prueba-json
				profile.reader = json
				field.id = id
				expected = id
				"""));

		assertThatThrownBy(() -> RequirementSource.of(p).extraer(new StringReader("{roto")))
				.isInstanceOf(java.io.IOException.class)
				.hasMessageContaining("no se pudo analizar");
	}
}
