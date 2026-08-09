package org.slcp.service.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.domain.Invitation;
import org.slcp.service.domain.ProjectRole;

/** Oraculo de la invitacion. */
class InvitationTest {

	private static final Instant T0 = Instant.parse("2026-08-08T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	private Invitation nueva() {
		return Invitation.emitir("resumen", PROYECTO, "  Nuevo@UTEQ.edu.ec ",
				ProjectRole.TEAM_MEMBER, QUIEN, T0, T0.plus(Duration.ofDays(7)));
	}

	@Test
	@DisplayName("INV-01: el correo se normaliza al emitir")
	void correoNormalizado() {
		assertThat(nueva().getEmail()).isEqualTo("nuevo@uteq.edu.ec");
	}

	@Test
	@DisplayName("Una invitacion recien emitida esta vigente")
	void vigente() {
		Invitation i = nueva();

		assertThat(i.estaVigente(T0)).isTrue();
		assertThat(i.motivoDeRechazo(T0)).isEmpty();
	}

	@Test
	@DisplayName("INV-01: es de un solo uso")
	void unSoloUso() {
		Invitation i = nueva();
		i.consumir(T0);

		assertThat(i.estaVigente(T0)).isFalse();
		assertThat(i.motivoDeRechazo(T0)).contains("ya se uso");
		assertThatThrownBy(() -> i.consumir(T0)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("INV-01: caduca al vencer el plazo")
	void caduca() {
		Invitation i = nueva();
		Instant tarde = T0.plus(Duration.ofDays(8));

		assertThat(i.estaVigente(tarde)).isFalse();
		assertThat(i.motivoDeRechazo(tarde)).contains("caduco");
	}

	@Test
	@DisplayName("Revocarla la invalida de inmediato")
	void revocada() {
		Invitation i = nueva();
		i.revocar(T0, "Retirada por el facilitador");

		assertThat(i.estaVigente(T0)).isFalse();
		assertThat(i.motivoDeRechazo(T0)).contains("retirada");
	}

	@Test
	@DisplayName("Una invitacion consumida ya no puede revocarse: su estado no cambia")
	void consumidaNoSeRevoca() {
		Invitation i = nueva();
		i.consumir(T0);
		i.revocar(T0, "tarde");

		assertThat(i.getConsumedAt()).isEqualTo(T0);
		assertThat(i.getRevokedAt()).isNull();
	}

	@Test
	@DisplayName("INV-02: el rol viaja con la invitacion y no cambia")
	void rolFijo() {
		assertThat(nueva().getProjectRole()).isEqualTo(ProjectRole.TEAM_MEMBER);
	}

	@Test
	@DisplayName("Cada motivo de rechazo explica que hacer")
	void motivosUtiles() {
		Invitation usada = nueva();
		usada.consumir(T0);
		assertThat(usada.motivoDeRechazo(T0)).contains("invite de nuevo");

		Invitation caducada = nueva();
		assertThat(caducada.motivoDeRechazo(T0.plus(Duration.ofDays(8)))).contains("invite de nuevo");
	}
}
