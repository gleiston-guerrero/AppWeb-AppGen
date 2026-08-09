package org.slcp.service.administration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de la decision del administrador. */
class ApprovalDecisionTest {

	@Test
	@DisplayName("ROL-05: el rechazo sin motivo se detecta")
	void rechazoSinMotivo() {
		assertThat(new ApprovalDecision(false, null).rechazoSinMotivo()).isTrue();
		assertThat(new ApprovalDecision(false, "   ").rechazoSinMotivo()).isTrue();
	}

	@Test
	@DisplayName("El rechazo con motivo es admisible")
	void rechazoConMotivo() {
		assertThat(new ApprovalDecision(false, "No consta vinculacion con la institucion")
				.rechazoSinMotivo()).isFalse();
	}

	@Test
	@DisplayName("La aprobacion no exige motivo")
	void aprobacionSinMotivo() {
		assertThat(new ApprovalDecision(true, null).rechazoSinMotivo()).isFalse();
	}
}
