package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la maquina de estados de la cuenta.
 *
 * <p>No requiere base de datos ni contexto de Spring: comprueba reglas y nada
 * mas, de modo que su resultado no depende de la infraestructura.</p>
 */
class UserStatusTest {

	@Test
	@DisplayName("FUN-16: desde pendiente solo se puede aprobar o rechazar")
	void desdePendiente() {
		assertThat(UserStatus.PENDING_APPROVAL.puedeTransitarA(UserStatus.ACTIVE)).isTrue();
		assertThat(UserStatus.PENDING_APPROVAL.puedeTransitarA(UserStatus.REJECTED)).isTrue();
		assertThat(UserStatus.PENDING_APPROVAL.puedeTransitarA(UserStatus.DECOMMISSIONED)).isFalse();
	}

	@Test
	@DisplayName("ADM-01: una cuenta activa solo puede darse de baja")
	void desdeActiva() {
		assertThat(UserStatus.ACTIVE.puedeTransitarA(UserStatus.DECOMMISSIONED)).isTrue();
		assertThat(UserStatus.ACTIVE.puedeTransitarA(UserStatus.PENDING_APPROVAL)).isFalse();
		assertThat(UserStatus.ACTIVE.puedeTransitarA(UserStatus.REJECTED)).isFalse();
	}

	@Test
	@DisplayName("ADM-05: la baja es reversible mediante reactivacion")
	void bajaReversible() {
		assertThat(UserStatus.DECOMMISSIONED.puedeTransitarA(UserStatus.ACTIVE)).isTrue();
	}

	@Test
	@DisplayName("El rechazo es terminal: no admite transicion alguna")
	void rechazoTerminal() {
		for (UserStatus destino : UserStatus.values()) {
			assertThat(UserStatus.REJECTED.puedeTransitarA(destino)).isFalse();
		}
	}
}
