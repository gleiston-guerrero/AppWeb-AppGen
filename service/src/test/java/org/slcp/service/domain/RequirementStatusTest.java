package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de las transiciones del requisito. */
class RequirementStatusTest {

	@Test
	@DisplayName("RQM-05: aprobar exige haber sido revisado antes")
	void aprobarExigeRevision() {
		assertThat(RequirementStatus.DRAFT.puedeTransitarA(RequirementStatus.APPROVED)).isFalse();
		assertThat(RequirementStatus.REVIEWED.puedeTransitarA(RequirementStatus.APPROVED)).isTrue();
	}

	@Test
	@DisplayName("Quien aprueba puede rectificar: de aprobado se puede reprobar")
	void aprobadoAdmiteRectificacion() {
		assertThat(RequirementStatus.APPROVED.puedeTransitarA(RequirementStatus.REJECTED)).isTrue();
		assertThat(RequirementStatus.APPROVED.puedeTransitarA(RequirementStatus.DRAFT)).isTrue();
	}

	@Test
	@DisplayName("Lo sustituido y lo anulado son terminales")
	void terminales() {
		for (RequirementStatus destino : RequirementStatus.values()) {
			assertThat(RequirementStatus.SUPERSEDED.puedeTransitarA(destino)).isFalse();
			assertThat(RequirementStatus.ANNULLED.puedeTransitarA(destino)).isFalse();
		}
	}

	@Test
	@DisplayName("Lo reprobado vuelve a borrador para corregirse")
	void reprobadoVuelveABorrador() {
		assertThat(RequirementStatus.REJECTED.puedeTransitarA(RequirementStatus.DRAFT)).isTrue();
		assertThat(RequirementStatus.REJECTED.puedeTransitarA(RequirementStatus.APPROVED)).isFalse();
	}

	@Test
	@DisplayName("Solo borrador y revisado admiten edicion directa")
	void edicionSegunEstado() {
		assertThat(RequirementStatus.DRAFT.admiteEdicion()).isTrue();
		assertThat(RequirementStatus.REVIEWED.admiteEdicion()).isTrue();
		assertThat(RequirementStatus.APPROVED.admiteEdicion()).isFalse();
	}
}
