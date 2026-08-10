package org.slcp.service.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.domain.PasswordReset;

/** Oraculo del enlace de recuperacion. */
class PasswordResetTest {

	private static final Instant T0 = Instant.parse("2026-08-10T10:00:00Z");
	private static final UUID CUENTA = UUID.randomUUID();

	private PasswordReset nuevo() {
		return PasswordReset.emitir("resumen", CUENTA, "10.0.0.1", T0,
				T0.plus(Duration.ofMinutes(30)));
	}

	@Test
	@DisplayName("Un enlace recien emitido esta vigente")
	void vigente() {
		assertThat(nuevo().estaVigente(T0)).isTrue();
		assertThat(nuevo().motivoDeRechazo(T0)).isEmpty();
	}

	@Test
	@DisplayName("SEC-06: es de un solo uso")
	void unSoloUso() {
		PasswordReset r = nuevo();
		r.consumir(T0);

		assertThat(r.estaVigente(T0)).isFalse();
		assertThat(r.motivoDeRechazo(T0)).contains("ya se uso");
		assertThatThrownBy(() -> r.consumir(T0)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("Caduca a los treinta minutos: quien lo pide lo usa en el momento")
	void caduca() {
		PasswordReset r = nuevo();
		Instant tarde = T0.plus(Duration.ofMinutes(31));

		assertThat(r.estaVigente(tarde)).isFalse();
		assertThat(r.motivoDeRechazo(tarde)).contains("caduco");
	}

	@Test
	@DisplayName("Revocarlo lo invalida, y el motivo explica por que")
	void revocado() {
		PasswordReset r = nuevo();
		r.revocar(T0);

		assertThat(r.estaVigente(T0)).isFalse();
		assertThat(r.motivoDeRechazo(T0)).contains("otro");
	}

	@Test
	@DisplayName("Un enlace ya usado no se revoca: su estado no cambia")
	void usadoNoSeRevoca() {
		PasswordReset r = nuevo();
		r.consumir(T0);
		r.revocar(T0);

		assertThat(r.motivoDeRechazo(T0)).contains("ya se uso");
	}

	@Test
	@DisplayName("Cada motivo dice que hacer, no solo que fallo")
	void motivosUtiles() {
		PasswordReset usado = nuevo();
		usado.consumir(T0);
		assertThat(usado.motivoDeRechazo(T0)).contains("Solicite otro");

		assertThat(nuevo().motivoDeRechazo(T0.plus(Duration.ofHours(1)))).contains("Solicite otro");
	}
}
