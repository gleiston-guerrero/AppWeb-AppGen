package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del generador de diagramas. */
class DerivedDiagramGeneratorTest {

	private final DerivedDiagramGenerator generador = new DerivedDiagramGenerator();

	private static final List<RequirementInput> REQUISITOS = List.of(
			new RequirementInput("REQ-0001-v1", "RF-01", "FUNCTIONAL", "Registrar parcela",
					"El responsable de la explotacion debera registrar una parcela [con corchetes] "
							+ "y \"comillas\".",
					"Registrar y comprobar.", "Quien lo pidio"),
			new RequirementInput("REQ-0004-v1", "RF-04", "FUNCTIONAL", "Activar el riego",
					"Cuando la humedad descienda del umbral, el sistema debera activar la valvula.",
					"Comprobar la orden.", "Sensor de humedad"),
			new RequirementInput("REQ-0008-v1", "RF-08", "FUNCTIONAL", "Exportar historial",
					"El sistema debera exportar el historial.", null, null));

	@Test
	@DisplayName("El actor sale del enunciado, no del interesado que trae la especificacion")
	void actorDelEnunciado() {
		// El campo "actor" de estos requisitos trae "Quien lo pidio" y "Sensor de
		// humedad": son el interesado y la fuente, no quien ejerce el caso de uso.
		ArtifactProposal p = generador.generar(REQUISITOS, DerivedDiagramGenerator.CASOS_DE_USO)
				.get(0);

		assertThat(p.content()).startsWith("graph LR");
		assertThat(p.content()).contains("Responsable de la explotacion");
		assertThat(p.content()).doesNotContain("Quien lo pidio").doesNotContain("Sensor de humedad");
	}

	@Test
	@DisplayName("El sistema no aparece como actor")
	void sistemaNoEsActor() {
		ArtifactProposal p = generador.generar(REQUISITOS, DerivedDiagramGenerator.CASOS_DE_USO)
				.get(0);

		for (String linea : p.content().split("\n")) {
			if (linea.contains("([")) {
				assertThat(linea).doesNotContain("\"Sistema\"");
			}
		}
	}

	@Test
	@DisplayName("Los requisitos cuyo actor no puede identificarse se ven, no se ocultan")
	void sinActorSeVe() {
		// Omitirlos daria un dibujo mas limpio y ocultaria una carencia: que el
		// enunciado no diga a quien sirve el sistema es informacion.
		ArtifactProposal p = generador.generar(REQUISITOS, DerivedDiagramGenerator.CASOS_DE_USO)
				.get(0);

		assertThat(p.content()).contains("Sin identificar");
		assertThat(p.needsDecision()).isTrue();
		assertThat(p.rationale()).contains("conocer el dominio");
	}

	@Test
	@DisplayName("Se escapa lo que Mermaid interpreta")
	void escapaMermaid() {
		// Un corchete cierra la caja antes de tiempo y el diagrama deja de
		// dibujarse entero, sin decir por que.
		ArtifactProposal p = generador.generar(REQUISITOS, DerivedDiagramGenerator.CASOS_DE_USO)
				.get(0);

		for (String linea : p.content().split("\n")) {
			if (linea.contains("RF-01")) {
				assertThat(linea).doesNotContain("[con corchetes]").doesNotContain("\"comillas\"");
			}
		}
	}

	@Test
	@DisplayName("El diagrama de estados solo sale de requisitos con condicion y cambio")
	void estadosSoloConCondicion() {
		List<ArtifactProposal> ps = generador.generar(REQUISITOS, DerivedDiagramGenerator.ESTADOS);

		// Solo RF-04 enuncia una condicion y una accion de cambio.
		assertThat(ps).hasSize(1);
		assertThat(ps.get(0).title()).contains("RF-04");
		assertThat(ps.get(0).content()).startsWith("stateDiagram-v2");
	}

	@Test
	@DisplayName("La condicion de vuelta queda como hueco: el requisito no la dice")
	void vueltaComoHueco() {
		ArtifactProposal p = generador.generar(REQUISITOS, DerivedDiagramGenerator.ESTADOS).get(0);

		assertThat(p.content()).contains("[indique la condicion de vuelta]");
		assertThat(p.needsDecision()).isTrue();
	}

	@Test
	@DisplayName("El mapa de trazabilidad distingue lo que tiene criterio")
	void trazabilidadDistingueCriterio() {
		ArtifactProposal p = generador.generar(REQUISITOS, DerivedDiagramGenerator.TRAZABILIDAD)
				.get(0);

		assertThat(p.content()).startsWith("graph TD");
		assertThat(p.rationale()).contains("sin criterio");
	}

	@Test
	@DisplayName("Sin requisitos no se dibuja nada")
	void sinRequisitosNadaQueDibujar() {
		for (String clase : generador.clases()) {
			assertThat(generador.generar(List.of(), clase)).isEmpty();
		}
	}
}
