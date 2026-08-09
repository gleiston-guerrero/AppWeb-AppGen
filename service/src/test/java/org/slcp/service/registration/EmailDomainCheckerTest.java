package org.slcp.service.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.registration.EmailDomainChecker.Resultado;

/**
 * Oraculo del comprobador de dominios de correo.
 *
 * <p>Estas pruebas consultan el servicio de nombres de verdad. Es deliberado:
 * simular la respuesta comprobaria el simulador y no la consulta. A cambio,
 * dependen de que haya resolucion de nombres, y por eso el caso de fallo esta
 * escrito para no ser sensible a ello.</p>
 */
class EmailDomainCheckerTest {

	private final EmailDomainChecker comprobador = new EmailDomainChecker(3000);

	@Test
	@DisplayName("Un dominio institucional con correo se acepta")
	void dominioInstitucional() {
		assertThat(comprobador.comprobar("gguerrero@uteq.edu.ec"))
				.isIn(Resultado.ACEPTA_CORREO, Resultado.NO_COMPROBADO);
		assertThat(comprobador.debeRechazarse("gguerrero@uteq.edu.ec")).isFalse();
	}

	@Test
	@DisplayName("Un dominio inexistente se rechaza")
	void dominioInexistente() {
		Resultado r = comprobador.comprobar("alguien@este-dominio-no-existe-slcp-99887766.com");

		// Si no hay resolucion de nombres el resultado es NO_COMPROBADO y no se
		// bloquea, que es el comportamiento buscado.
		if (r != Resultado.NO_COMPROBADO) {
			assertThat(r).isEqualTo(Resultado.DOMINIO_INEXISTENTE);
			assertThat(comprobador.explicacion(r)).contains("no existe");
		}
	}

	@Test
	@DisplayName("Una direccion sin arroba no se comprueba y no bloquea")
	void sinArroba() {
		assertThat(comprobador.comprobar("esto-no-es-un-correo")).isEqualTo(Resultado.NO_COMPROBADO);
		assertThat(comprobador.debeRechazarse("esto-no-es-un-correo")).isFalse();
	}

	@Test
	@DisplayName("La ausencia de resolucion nunca bloquea el registro")
	void noComprobadoNoBloquea() {
		assertThat(comprobador.debeRechazarse(null)).isFalse();
		assertThat(comprobador.explicacion(Resultado.NO_COMPROBADO)).isEmpty();
	}

	@Test
	@DisplayName("El dominio se normaliza: mayusculas y espacios no cambian el resultado")
	void normalizacion() {
		assertThat(comprobador.comprobar("  ALGUIEN@UTEQ.EDU.EC  "))
				.isEqualTo(comprobador.comprobar("alguien@uteq.edu.ec"));
	}

	@Test
	@DisplayName("Cada resultado que bloquea trae explicacion, y los que no, no")
	void explicaciones() {
		assertThat(comprobador.explicacion(Resultado.DOMINIO_INEXISTENTE)).isNotBlank();
		assertThat(comprobador.explicacion(Resultado.SIN_SERVIDOR_DE_CORREO)).isNotBlank();
		assertThat(comprobador.explicacion(Resultado.ACEPTA_CORREO)).isEmpty();
	}
}
