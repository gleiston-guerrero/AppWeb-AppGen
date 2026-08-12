package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la comparacion por narrativa.
 *
 * <p>Los enunciados de estas pruebas salen de dos especificaciones reales del
 * mismo sistema, escritas en formatos distintos y numeradas cada una desde uno.
 * Es el caso que motivo la comparacion.</p>
 */
class StatementSimilarityTest {

	@Test
	@DisplayName("El mismo enunciado con distinto identificador es el mismo requisito")
	void mismoEnunciadoOtroNumero() {
		String uno = "Cuando la humedad media de una parcela descienda por debajo del umbral "
				+ "configurado para su cultivo, el sistema debera activar la valvula de riego.";
		String otro = "Cuando la humedad media de una parcela descienda por debajo del umbral "
				+ "configurado para su cultivo, el sistema debera activar la valvula de riego.";

		assertThat(StatementSimilarity.sonElMismo(uno, otro)).isTrue();
	}

	@Test
	@DisplayName("Lo mismo dicho con otras palabras sigue siendo lo mismo")
	void mismaExigenciaOtraRedaccion() {
		String uno = "El sistema debera registrar una parcela de cultivo con su identificador, "
				+ "superficie en hectareas, tipo de suelo y cultivo sembrado.";
		String otro = "El sistema registrara cada parcela de cultivo indicando identificador, "
				+ "superficie en hectareas, tipo del suelo y el cultivo sembrado.";

		assertThat(StatementSimilarity.entre(uno, otro))
				.isGreaterThanOrEqualTo(StatementSimilarity.UMBRAL_DUPLICADO);
	}

	@Test
	@DisplayName("Dos requisitos distintos con el mismo identificador no son el mismo")
	void distintosMismoNumero() {
		// Ambos llegaron como RF-02 en documentos diferentes.
		String uno = "El sistema debera almacenar cada lectura de humedad del suelo recibida de un "
				+ "sensor, junto con su marca de tiempo y la parcela.";
		String otro = "El sistema debera dar de alta un sensor asociandolo a una parcela, con su "
				+ "tipo de medida, su periodo de muestreo y su ubicacion.";

		assertThat(StatementSimilarity.sonElMismo(uno, otro)).isFalse();
	}

	@Test
	@DisplayName("Requisitos de asuntos distintos quedan lejos")
	void asuntosDistintos() {
		String uno = "El sistema debera registrar cada cabeza de ganado con su crotal, especie y raza.";
		String otro = "El sistema debera exportar el historial de riego en un archivo de valores "
				+ "separados por comas.";

		assertThat(StatementSimilarity.entre(uno, otro)).isLessThan(0.3);
	}

	@Test
	@DisplayName("Las palabras de relleno no acercan requisitos ajenos")
	void rellenoNoAcerca() {
		// Casi todo enunciado empieza igual: si esas palabras contaran, cualquier
		// par de requisitos se pareceria.
		String uno = "El sistema debera mostrar el panel de la explotacion.";
		String otro = "El sistema debera exportar el historial de riego.";

		assertThat(StatementSimilarity.entre(uno, otro)).isLessThan(0.4);
	}

	@Test
	@DisplayName("Un enunciado ampliado con detalles sigue siendo el mismo requisito")
	void ampliacionSigueSiendoElMismo() {
		String breve = "El sistema debera exportar el historial de riego de una explotacion.";
		String amplio = "El sistema debera exportar el historial de riego de una explotacion en un "
				+ "archivo de valores separados por comas, con una fila por riego.";

		assertThat(StatementSimilarity.entre(breve, amplio))
				.isGreaterThanOrEqualTo(StatementSimilarity.UMBRAL_SOSPECHA);
	}

	@Test
	@DisplayName("La comparacion no depende de tildes ni de mayusculas")
	void indiferenteATildes() {
		assertThat(StatementSimilarity.sonElMismo(
				"El sistema deberá registrar la parcela con su superficie.",
				"EL SISTEMA DEBERA REGISTRAR LA PARCELA CON SU SUPERFICIE.")).isTrue();
	}

	@Test
	@DisplayName("Las formas de una misma palabra cuentan como una")
	void formasDeLaMismaPalabra() {
		assertThat(StatementSimilarity.tokens("registrar registra registros"))
				.hasSize(3)
				.allMatch(t -> t.startsWith("registr"));
	}

	@Test
	@DisplayName("Un enunciado vacio no se parece a nada")
	void vacioNoSeParece() {
		assertThat(StatementSimilarity.entre("", "El sistema debera registrar la parcela.")).isZero();
		assertThat(StatementSimilarity.entre(null, "algo")).isZero();
	}

	@Test
	@DisplayName("La franja de sospecha queda por debajo de la de duplicado")
	void umbralesCoherentes() {
		assertThat(StatementSimilarity.UMBRAL_SOSPECHA)
				.isLessThan(StatementSimilarity.UMBRAL_DUPLICADO);
	}
}
