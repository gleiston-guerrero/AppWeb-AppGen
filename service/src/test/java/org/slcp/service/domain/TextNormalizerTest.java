package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de la unificacion de formato. */
class TextNormalizerTest {

	@Test
	@DisplayName("Un texto entero en mayusculas se baja")
	void mayusculasSeBajan() {
		assertThat(TextNormalizer.nombre("REGISTRAR PARCELA DE CULTIVO"))
				.isEqualTo("Registrar parcela de cultivo");
	}

	@Test
	@DisplayName("Un texto entero en minusculas recibe su mayuscula inicial")
	void minusculasSeCapitalizan() {
		assertThat(TextNormalizer.nombre("registrar parcela de cultivo"))
				.isEqualTo("Registrar parcela de cultivo");
	}

	@Test
	@DisplayName("Las siglas se conservan al bajar un texto en mayusculas")
	void siglasSeConservan() {
		// Bajar "CSV" a "csv" convertiria una sigla en una palabra que no existe.
		assertThat(TextNormalizer.nombre("EXPORTAR EL HISTORIAL EN CSV"))
				.isEqualTo("Exportar el historial en CSV");
	}

	@Test
	@DisplayName("Un texto con caja mezclada se respeta")
	void cajaMezcladaSeRespeta() {
		// Quien lo escribio decidio donde iban las mayusculas, y ahi puede haber
		// nombres propios que este metodo no sabria distinguir.
		assertThat(TextNormalizer.nombre("Registrar parcela en La Mancha"))
				.isEqualTo("Registrar parcela en La Mancha");
	}

	@Test
	@DisplayName("Los espacios sobrantes se colapsan")
	void espaciosSeColapsan() {
		assertThat(TextNormalizer.nombre("  registrar    la   parcela  "))
				.isEqualTo("Registrar la parcela");
	}

	@Test
	@DisplayName("Un enunciado termina en punto")
	void enunciadoTerminaEnPunto() {
		assertThat(TextNormalizer.enunciado("el sistema debera registrar la parcela"))
				.isEqualTo("El sistema debera registrar la parcela.");
	}

	@Test
	@DisplayName("No se anade un segundo punto al que ya lo tiene")
	void sinPuntoDoble() {
		assertThat(TextNormalizer.enunciado("El sistema debera registrar la parcela."))
				.endsWith("parcela.")
				.doesNotContain("..");
	}

	@Test
	@DisplayName("Un texto vacio o nulo se resuelve como nulo")
	void vacioEsNulo() {
		assertThat(TextNormalizer.nombre("   ")).isNull();
		assertThat(TextNormalizer.nombre(null)).isNull();
		assertThat(TextNormalizer.enunciado("")).isNull();
	}
}
