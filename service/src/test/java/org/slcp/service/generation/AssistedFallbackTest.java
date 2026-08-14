package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.AiProvider;

/**
 * Oraculo del respaldo de la generacion asistida.
 *
 * <p>La generacion con IA mejora el resultado y no puede ser condicion para
 * trabajar (ANA-06). Si estas pruebas dejan de pasar, un corte de red o una
 * clave caducada dejarian sin generar pruebas a quien solo queria una.</p>
 */
class AssistedFallbackTest {

	private static final RequirementInput REQUISITO = new RequirementInput(
			"REQ-0001-v1", "RF-01", "FUNCTIONAL", "Registrar parcela de cultivo",
			"El sistema debera registrar una parcela de cultivo con su superficie.",
			"Con datos validos, registrar una parcela y comprobar que aparece en el listado.",
			null);

	/** Un servicio que no responde: red caida, clave caducada o direccion mal. */
	private TestGenerator conServicioCaido() {
		// La instruccion se recibe: es de la funcion y comun a todas las APIs.
		return new AssistedTestGenerator(new DerivedTestGenerator(), AiProvider.ANTHROPIC,
				"http://127.0.0.1:9/no-existe", "clave-invalida", "modelo",
				PromptCatalog.porDefecto(AiFeature.GENERATE_TESTS), Duration.ofSeconds(1));
	}

	@Test
	@DisplayName("Si el servicio no responde, se genera igual por la via derivada")
	void respaldoAlDerivado() {
		List<ArtifactProposal> ps = conServicioCaido()
				.generar(REQUISITO, DerivedTestGenerator.ACEPTACION);

		assertThat(ps).hasSize(1);
		assertThat(ps.get(0).content()).contains("Escenario:").contains("Dado").contains("Cuando");
	}

	@Test
	@DisplayName("El respaldo produce el escenario completo, no una plantilla vacia")
	void respaldoCompleto() {
		ArtifactProposal p = conServicioCaido()
				.generar(REQUISITO, DerivedTestGenerator.ACEPTACION).get(0);

		assertThat(p.content()).contains("Dado que se parte de datos validos");
		assertThat(p.content()).contains("Cuando se registra una parcela");
		assertThat(p.content()).contains("Entonces aparece en el listado");
	}

	@Test
	@DisplayName("El fundamento dice la verdad: no se atribuye al modelo lo que no escribio")
	void procedenciaHonesta() {
		// Decir que lo redacto un modelo cuando el servicio no contesto seria falso
		// justo cuando mas importa saberlo.
		ArtifactProposal p = conServicioCaido()
				.generar(REQUISITO, DerivedTestGenerator.ACEPTACION).get(0);

		assertThat(p.rationale()).doesNotContain("Redactada por el modelo");
		assertThat(p.rationale()).contains("criterio de verificacion");
	}

	@Test
	@DisplayName("Todas las clases siguen disponibles con el servicio caido")
	void todasLasClases() {
		TestGenerator g = conServicioCaido();

		assertThat(g.clases()).containsExactlyInAnyOrderElementsOf(
				new DerivedTestGenerator().clases());
	}

	@Test
	@DisplayName("El respaldo no inventa lo que la via derivada no puede derivar")
	void respaldoNoInventa() {
		// Sin magnitudes en el requisito no hay limite que probar, y el respaldo
		// tampoco lo fabrica.
		assertThat(conServicioCaido().generar(REQUISITO, DerivedTestGenerator.LIMITE)).isEmpty();
	}

	@Test
	@DisplayName("Sin credencial, el generador se declara no disponible")
	void sinCredencialNoDisponible() {
		TestGenerator sinClave = new AssistedTestGenerator(new DerivedTestGenerator(),
				AiProvider.ANTHROPIC, "http://localhost", "", "modelo",
				PromptCatalog.porDefecto(AiFeature.GENERATE_TESTS), Duration.ofSeconds(1));

		assertThat(sinClave.generar(REQUISITO, DerivedTestGenerator.ACEPTACION)).hasSize(1);
	}
}
