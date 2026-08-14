package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la prevalencia de la decision del equipo.
 *
 * <p>La comprobacion informa; no decide. Si alguna de estas pruebas deja de
 * pasar, la plataforma ha vuelto a ponerse por encima de quien responde del
 * sistema.</p>
 */
class ArtifactDecisionTest {

	private static final Instant T0 = Instant.parse("2026-08-13T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	private GeneratedArtifact conHuecos(boolean huecos) {
		return GeneratedArtifact.crear(PROYECTO, "PRB-0001", "TEST", "ACCEPTANCE",
				"Aceptacion de RF-01", "Escenario: algo\n  Entonces [indique el valor]",
				"GHERKIN", GeneratedArtifact.DERIVADO, "Derivada del criterio", huecos, QUIEN, T0);
	}

	@Test
	@DisplayName("Un artefacto con huecos puede aceptarse: la decision es del equipo")
	void seAceptaConHuecos() {
		// Impedirlo convertiria una advertencia en un veto. Puede haber razones para
		// dar por bueno algo incompleto, y quien las conoce es el equipo.
		GeneratedArtifact a = conHuecos(true);
		a.aceptar(QUIEN, T0);

		assertThat(a.getStatus()).isEqualTo(GeneratedArtifact.ACEPTADO);
	}

	@Test
	@DisplayName("Queda constancia de que se acepto habiendo huecos")
	void constaQueHabiaHuecos() {
		// Aceptar sobre un aviso es legitimo; que despues no se sepa que lo habia, no.
		GeneratedArtifact a = conHuecos(true);
		a.aceptar(QUIEN, T0);

		assertThat(a.isAcceptedWithGaps()).isTrue();
	}

	@Test
	@DisplayName("Sin huecos, no consta que los hubiera")
	void sinHuecosNoConsta() {
		GeneratedArtifact a = conHuecos(false);
		a.aceptar(QUIEN, T0);

		assertThat(a.isAcceptedWithGaps()).isFalse();
		assertThat(a.getStatus()).isEqualTo(GeneratedArtifact.ACEPTADO);
	}

	@Test
	@DisplayName("La aceptacion consta de quien la tomo")
	void constaElAutor() {
		GeneratedArtifact a = conHuecos(true);
		a.aceptar(QUIEN, T0);

		assertThat(a.getReviewedBy()).isEqualTo(QUIEN);
	}

	@Test
	@DisplayName("Modificarlo lo devuelve a propuesto y pasa a constar como humano")
	void modificarDevuelveAPropuesto() {
		GeneratedArtifact a = conHuecos(true);
		a.aceptar(QUIEN, T0);
		a.editar(null, "Escenario: algo\n  Entonces el sistema responde", T0);

		assertThat(a.getStatus()).isEqualTo(GeneratedArtifact.PROPUESTO);
		assertThat(a.getOrigin()).isEqualTo(GeneratedArtifact.HUMANO);
		assertThat(a.isNeedsDecision()).isFalse();
	}
}
