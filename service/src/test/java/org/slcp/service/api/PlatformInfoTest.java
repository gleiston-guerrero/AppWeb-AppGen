package org.slcp.service.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la informacion publica de la plataforma.
 *
 * <p>Deliberadamente no levanta el contexto de Spring: comprueba el contenido y
 * nada mas, de modo que su resultado no depende de la infraestructura. La
 * comprobacion de que el contexto arranca corresponde a la otra prueba.</p>
 */
class PlatformInfoTest {

	@Test
	@DisplayName("FUN-01: declara el insumo de entrada y lo que produce")
	void declaraInsumoYProduccion() {
		PlatformInfo info = PlatformInfo.current("0.1.0-TEST");

		assertThat(info.input()).containsIgnoringCase("requisitos");
		assertThat(info.produces()).isNotEmpty();
		assertThat(info.purpose()).isNotBlank();
	}

	@Test
	@DisplayName("FUN-02: declara autoria, institucion y licencia")
	void declaraAutoriaYLicencia() {
		PlatformInfo info = PlatformInfo.current("0.1.0-TEST");

		assertThat(info.authorship()).containsIgnoringCase("Quevedo");
		assertThat(info.license()).isEqualTo("MIT");
		assertThat(info.repository()).startsWith("https://github.com/");
	}

	@Test
	@DisplayName("FUN-01: no expone dato alguno de ningun proyecto")
	void noExponeDatosDeProyecto() {
		PlatformInfo info = PlatformInfo.current("0.1.0-TEST");

		assertThat(info.toString().toLowerCase())
				.doesNotContain("proyecto:")
				.doesNotContain("usuario")
				.doesNotContain("contrase");
	}

	@Test
	@DisplayName("La version se recibe, no se codifica en el objeto")
	void versionRecibida() {
		assertThat(PlatformInfo.current("9.9.9").version()).isEqualTo("9.9.9");
	}
}
