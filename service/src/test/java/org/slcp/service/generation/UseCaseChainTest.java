package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la cadena requisito -> caso de uso -> pruebas y diagramas.
 *
 * <p>Un requisito dice que debe hacer el sistema; un caso de uso dice como
 * transcurre y --- lo que ningun requisito trae --- que ocurre cuando algo
 * falla. Derivar de el reduce lo que hay que inventar, y estas pruebas vigilan
 * que asi sea.</p>
 */
class UseCaseChainTest {

	private final DerivedTestGenerator generador = new DerivedTestGenerator();

	private static final String ENUNCIADO =
			"El sistema debera registrar una parcela de cultivo con su superficie.";

	private static final String CRITERIO =
			"Con datos validos, registrar una parcela y comprobar que aparece en el listado.";

	/** Un caso de uso aceptado, con sus flujos de excepcion. */
	private static final String CASO_DE_USO = """
			{"nombre":"Registrar parcela","actorPrincipal":"Responsable de la explotacion",
			 "actoresSecundarios":["Tecnico agronomo"],
			 "flujosExcepcionales":[
			   {"numero":"E1","condicion":"Falta la superficie (paso 2)",
			    "respuesta":"El sistema impide continuar y senala el campo. El flujo retorna al paso 2",
			    "desdeElPaso":2},
			   {"numero":"E2","condicion":"El identificador ya esta en uso (paso 3)",
			    "respuesta":"El sistema informa del conflicto","desdeElPaso":3}]}
			""";

	private RequirementInput sinCasoDeUso() {
		return new RequirementInput("REQ-0001-v1", "RF-01", "FUNCTIONAL", "Registrar parcela",
				ENUNCIADO, CRITERIO, null);
	}

	private RequirementInput conCasoDeUso() {
		return new RequirementInput("REQ-0001-v1", "RF-01", "FUNCTIONAL", "Registrar parcela",
				ENUNCIADO, CRITERIO, "Responsable", CASO_DE_USO);
	}

	@Test
	@DisplayName("Sin caso de uso, el camino negativo deja hueco el resultado")
	void sinCasoDeUsoQuedaHueco() {
		// El requisito no dice que ha de ocurrir cuando la condicion no se cumple.
		ArtifactProposal p = generador.generar(sinCasoDeUso(), DerivedTestGenerator.NEGATIVA)
				.get(0);

		assertThat(p.needsDecision()).isTrue();
		assertThat(p.content()).contains(DerivedTestGenerator.HUECO);
	}

	@Test
	@DisplayName("Con caso de uso aceptado, el camino negativo sale completo")
	void conCasoDeUsoSaleCompleto() {
		// La condicion y la respuesta las decidio el equipo: no falta nada.
		ArtifactProposal p = generador.generar(conCasoDeUso(), DerivedTestGenerator.NEGATIVA)
				.get(0);

		assertThat(p.needsDecision()).isFalse();
		assertThat(p.content()).doesNotContain(DerivedTestGenerator.HUECO);
	}

	@Test
	@DisplayName("Se genera un escenario por cada flujo de excepcion")
	void unEscenarioPorExcepcion() {
		ArtifactProposal p = generador.generar(conCasoDeUso(), DerivedTestGenerator.NEGATIVA)
				.get(0);

		assertThat(p.content().split("Escenario:")).hasSize(3);
		assertThat(p.content()).contains("falta la superficie");
		assertThat(p.content()).contains("el identificador ya esta en uso");
	}

	@Test
	@DisplayName("La respuesta del flujo se convierte en el resultado esperado")
	void respuestaAlEntonces() {
		ArtifactProposal p = generador.generar(conCasoDeUso(), DerivedTestGenerator.NEGATIVA)
				.get(0);

		assertThat(p.content()).contains("Entonces el sistema impide continuar");
		assertThat(p.content()).contains("Entonces el sistema informa del conflicto");
	}

	@Test
	@DisplayName("Las referencias al caso de uso no viajan a la prueba")
	void sinReferenciasAlDocumento() {
		// "(paso 2)" y "el flujo retorna al paso 2" remiten a un documento que quien
		// ejecuta la prueba no tiene delante.
		ArtifactProposal p = generador.generar(conCasoDeUso(), DerivedTestGenerator.NEGATIVA)
				.get(0);

		assertThat(p.content()).doesNotContain("(paso 2)");
		assertThat(p.content()).doesNotContain("retorna al paso");
	}

	@Test
	@DisplayName("El fundamento dice de donde sale, para poder juzgarlo")
	void fundamentoHonesto() {
		ArtifactProposal p = generador.generar(conCasoDeUso(), DerivedTestGenerator.NEGATIVA)
				.get(0);

		assertThat(p.rationale()).contains("flujos de excepcion del caso de uso aceptado");
	}

	// --- Actores ---

	@Test
	@DisplayName("Sin caso de uso, el actor no puede identificarse en este enunciado")
	void sinCasoDeUsoNoHayActor() {
		List<ActorExtractor.Identificado> is = ActorExtractor.identificar(ENUNCIADO);

		assertThat(is.get(0).actor()).isEqualTo(ActorExtractor.SIN_IDENTIFICAR);
	}

	@Test
	@DisplayName("Con caso de uso, el actor lo declara el equipo y no se deduce")
	void actorDeclarado() {
		List<ActorExtractor.Identificado> is =
				ActorExtractor.identificar(ENUNCIADO, CASO_DE_USO);

		assertThat(is).extracting(ActorExtractor.Identificado::actor)
				.contains("Responsable de la explotacion", "Tecnico agronomo");
		assertThat(is.get(0).porque()).contains("caso de uso aceptado");
	}

	@Test
	@DisplayName("Un caso de uso sin actores no impide caer al enunciado")
	void sinActoresCaeAlEnunciado() {
		// Si el documento no los trae, se vuelve a lo que se pueda deducir: es peor,
		// pero es mejor que no dar nada.
		List<ActorExtractor.Identificado> is = ActorExtractor.identificar(
				"El operario de campo debera calibrar el sensor.", "{\"nombre\":\"Calibrar\"}");

		assertThat(is.get(0).actor()).isEqualTo("Operario de campo");
	}
}
