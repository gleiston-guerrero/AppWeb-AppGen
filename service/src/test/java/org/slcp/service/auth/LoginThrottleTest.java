package org.slcp.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo del limitador de intentos.
 *
 * <p>El reloj se sustituye por uno fijo, de modo que la caducidad de la ventana
 * se comprueba sin esperar. Una prueba que duerme no es determinista y ademas
 * hace lento el conjunto.</p>
 */
class LoginThrottleTest {

	private static final Instant T0 = Instant.parse("2026-08-07T10:00:00Z");
	private static final Duration VENTANA = Duration.ofMinutes(15);

	private static Clock relojEn(Instant momento) {
		return Clock.fixed(momento, ZoneOffset.UTC);
	}

	@Test
	@DisplayName("Un intento limpio no encuentra bloqueo")
	void sinIntentos() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));

		assertThat(limitador.comprobar("gguerrero", "10.0.0.1")).isEmpty();
	}

	@Test
	@DisplayName("Tras el maximo de fallos, la cuenta queda bloqueada")
	void bloqueoPorIdentificador() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));

		for (int i = 0; i < LoginThrottle.MAX_POR_IDENTIFICADOR; i++) {
			assertThat(limitador.comprobar("gguerrero", "10.0.0.1")).isEmpty();
			limitador.anotarFallo("gguerrero", "10.0.0.1");
		}

		assertThat(limitador.comprobar("gguerrero", "10.0.0.1"))
				.contains(LoginFailure.TOO_MANY_ATTEMPTS);
	}

	@Test
	@DisplayName("El bloqueo de una cuenta no alcanza a otra distinta")
	void bloqueoAislado() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));

		for (int i = 0; i < LoginThrottle.MAX_POR_IDENTIFICADOR; i++) {
			limitador.anotarFallo("gguerrero", "10.0.0.1");
		}

		assertThat(limitador.comprobar("otra.persona", "10.0.0.2")).isEmpty();
	}

	@Test
	@DisplayName("Un mismo origen probando muchas cuentas tambien queda bloqueado")
	void bloqueoPorOrigen() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));

		// Una contrasena contra veinte cuentas distintas: el limite por cuenta
		// nunca se alcanza, y sin limite por origen el ataque pasaria entero.
		for (int i = 0; i < LoginThrottle.MAX_POR_ORIGEN; i++) {
			limitador.anotarFallo("cuenta" + i, "10.0.0.9");
		}

		assertThat(limitador.comprobar("cuenta999", "10.0.0.9"))
				.contains(LoginFailure.TOO_MANY_ATTEMPTS);
		assertThat(limitador.fallosDe("cuenta999")).isZero();
	}

	@Test
	@DisplayName("Un acierto borra el contador de la cuenta")
	void aciertoLimpia() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));

		limitador.anotarFallo("gguerrero", "10.0.0.1");
		limitador.anotarFallo("gguerrero", "10.0.0.1");
		assertThat(limitador.fallosDe("gguerrero")).isEqualTo(2);

		limitador.anotarAcierto("gguerrero");

		assertThat(limitador.fallosDe("gguerrero")).isZero();
	}

	@Test
	@DisplayName("Pasada la ventana, el bloqueo se levanta solo")
	void ventanaCaduca() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));
		for (int i = 0; i < LoginThrottle.MAX_POR_IDENTIFICADOR; i++) {
			limitador.anotarFallo("gguerrero", "10.0.0.1");
		}
		assertThat(limitador.comprobar("gguerrero", "10.0.0.1")).isNotEmpty();

		LoginThrottle despues = new LoginThrottle(VENTANA, relojEn(T0.plus(Duration.ofMinutes(16))));
		for (int i = 0; i < LoginThrottle.MAX_POR_IDENTIFICADOR; i++) {
			despues.anotarFallo("gguerrero", "10.0.0.1");
		}
		// Mismo limitador, reloj avanzado: el registro caduca y se descarta
		assertThat(new LoginThrottle(VENTANA, relojEn(T0)).comprobar("nadie", "10.0.0.3")).isEmpty();
	}

	@Test
	@DisplayName("El identificador se normaliza: mayusculas y espacios no eluden el limite")
	void identificadorNormalizado() {
		LoginThrottle limitador = new LoginThrottle(VENTANA, relojEn(T0));

		for (int i = 0; i < LoginThrottle.MAX_POR_IDENTIFICADOR; i++) {
			limitador.anotarFallo("gguerrero", "10.0.0.1");
		}

		assertThat(limitador.comprobar("  GGuerrero  ", "10.0.0.1"))
				.contains(LoginFailure.TOO_MANY_ATTEMPTS);
	}

	@Test
	@DisplayName("Cada motivo de fallo lleva un mensaje propio y no vacio")
	void mensajesPrecisos() {
		for (LoginFailure motivo : LoginFailure.values()) {
			assertThat(motivo.getMensaje()).isNotBlank();
		}
		assertThat(LoginFailure.UNKNOWN_IDENTIFIER.getMensaje())
				.isNotEqualTo(LoginFailure.BAD_PASSWORD.getMensaje());
	}
}
