package org.slcp.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * Oraculo de las cookies de sesion.
 *
 * <p>Comprueba las tres marcas de SEC-01 una por una, porque cada una ataja un
 * ataque distinto y omitir cualquiera deja abierto el suyo.</p>
 */
class SessionCookiesTest {

	@Test
	@DisplayName("SEC-01: la cookie de acceso no es legible por el codigo de la pagina")
	void accesoNoLegible() {
		ResponseCookie cookie = SessionCookies.acceso("slcp_access", "abc", Duration.ofMinutes(15), true);

		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.isSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Strict");
	}

	@Test
	@DisplayName("La cookie de renovacion se restringe a su propia ruta")
	void renovacionRestringida() {
		ResponseCookie cookie = SessionCookies.renovacion("slcp_refresh", "abc", Duration.ofDays(7), true);

		assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
		assertThat(cookie.isHttpOnly()).isTrue();
	}

	@Test
	@DisplayName("Borrar una cookie la caduca de inmediato y conserva sus marcas")
	void borradoInmediato() {
		ResponseCookie cookie = SessionCookies.borrar("slcp_access", "/api", true);

		assertThat(cookie.getMaxAge()).isZero();
		assertThat(cookie.getValue()).isEmpty();
		assertThat(cookie.isHttpOnly()).isTrue();
	}
}
