package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del generador derivado. */
class DerivedTestGeneratorTest {

	private final DerivedTestGenerator generador = new DerivedTestGenerator();

	private static final RequirementInput RIEGO = new RequirementInput(
			"REQ-0004-v1", "RF-04", "FUNCTIONAL", "Activar el riego por umbral",
			"Cuando la humedad media de una parcela descienda por debajo del umbral configurado, "
					+ "el sistema debera activar la valvula de riego.",
			"Con un umbral del 30 por ciento, comprobar que se emite la orden de apertura.",
			"Sistema");

	private static final RequirementInput SIN_CRITERIO = new RequirementInput(
			"REQ-0008-v1", "RF-08", "FUNCTIONAL", "Exportar el historial",
			"El sistema debera exportar el historial de riego.", null, null);

	private static final RequirementInput SIN_MAGNITUD = new RequirementInput(
			"REQ-0032-v1", "RNF-T4", "NON_FUNCTIONAL", "Disponibilidad",
			"El sistema debera mantenerse disponible durante el horario de operacion.",
			"Medir la disponibilidad durante un mes.", null);

	@Test
	@DisplayName("La prueba de aceptacion despieza el criterio en sus tres partes")
	void aceptacionTraduceElCriterio() {
		ArtifactProposal p = generador.generar(RIEGO, DerivedTestGenerator.ACEPTACION).get(0);

		assertThat(p.content()).contains("Escenario:").contains("Dado").contains("Cuando")
				.contains("Entonces");

		// El contexto del criterio va al Dado, no al resultado.
		assertThat(p.content()).contains("Dado que se parte de un umbral del 30 por ciento");
		assertThat(p.needsDecision()).isFalse();
	}

	@Test
	@DisplayName("Sin criterio, el resultado esperado queda como hueco")
	void sinCriterioDejaHueco() {
		// Derivar el resultado del enunciado seria inventar que basta para darlo
		// por cumplido.
		ArtifactProposal p = generador.generar(SIN_CRITERIO, DerivedTestGenerator.ACEPTACION).get(0);

		assertThat(p.content()).contains(DerivedTestGenerator.HUECO);
		assertThat(p.needsDecision()).isTrue();
		assertThat(p.rationale()).contains("no tiene criterio");
	}

	@Test
	@DisplayName("Las pruebas de limite salen de las magnitudes que el requisito declara")
	void limitesDeLasMagnitudes() {
		List<ArtifactProposal> ps = generador.generar(RIEGO, DerivedTestGenerator.LIMITE);

		assertThat(ps).isNotEmpty();
		assertThat(ps.get(0).content()).contains("30").contains("Ejemplos:");
		assertThat(ps.get(0).needsDecision()).isTrue();
	}

	@Test
	@DisplayName("Sin magnitudes no se inventa ningun limite")
	void sinMagnitudesSinLimites() {
		RequirementInput vago = new RequirementInput("REQ-0001-v1", "RF-01", "FUNCTIONAL",
				"Registrar parcela", "El sistema debera registrar una parcela de cultivo.",
				"Registrar una parcela y comprobar que aparece.", null);

		assertThat(generador.generar(vago, DerivedTestGenerator.LIMITE)).isEmpty();
	}

	@Test
	@DisplayName("El camino negativo deja como hueco lo que el requisito no dice")
	void negativoDejaHueco() {
		ArtifactProposal p = generador.generar(RIEGO, DerivedTestGenerator.NEGATIVA).get(0);

		assertThat(p.content()).contains("Dado que NO se cumple");
		assertThat(p.content()).contains(DerivedTestGenerator.HUECO);
		assertThat(p.rationale()).contains("no que ha de ocurrir");
	}

	@Test
	@DisplayName("La prueba de rendimiento solo se genera para requisitos no funcionales")
	void rendimientoSoloNoFuncional() {
		assertThat(generador.generar(RIEGO, DerivedTestGenerator.RENDIMIENTO)).isEmpty();
	}

	@Test
	@DisplayName("Un requisito no funcional sin magnitud avisa en lugar de inventarla")
	void sinMagnitudAvisa() {
		ArtifactProposal p = generador.generar(SIN_MAGNITUD, DerivedTestGenerator.RENDIMIENTO).get(0);

		assertThat(p.content()).contains("no declara ninguna magnitud");
		assertThat(p.needsDecision()).isTrue();
		assertThat(p.rationale()).contains("nadie decidio");
	}

	@Test
	@DisplayName("Ninguna prueba derivada introduce una cifra que el requisito no traiga")
	void sinCifrasInventadas() {
		for (String clase : generador.clases()) {
			for (ArtifactProposal p : generador.generar(RIEGO, clase)) {
				// 30 es del requisito; cualquier otra cifra de magnitud seria inventada.
				assertThat(p.content().replaceAll("30", ""))
						.doesNotMatch("(?s).*\\b\\d+\\s*(por ciento|segundos?|litros?)\\b.*");
			}
		}
	}

	@Test
	@DisplayName("El contexto se antepone con una formula que siempre concuerda")
	void premisaLegible() {
		// "Dado que se parte de X" concuerda con cualquier X, singular o plural.
		// "Dado datos validos" no concuerda.
		ArtifactProposal p = generador.generar(RIEGO, DerivedTestGenerator.ACEPTACION).get(0);

		assertThat(p.content()).contains("Dado que se parte de");
	}
}
