package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.ingestion.CriterionSuggester.Suggestion;

/** Oraculo del sugeridor determinista de criterios. */
class CriterionSuggesterTest {

	private final CriterionSuggester sugeridor = new RuleBasedCriterionSuggester();

	private static final String CON_ACCION =
			"El sistema debera permitir al propietario registrar una mascota ingresando nombre y especie.";

	@Test
	@DisplayName("ANA-20: propone mas de una redaccion, nunca una sola")
	void variasOpciones() {
		assertThat(sugeridor.proponer(CON_ACCION, false)).hasSizeGreaterThan(1);
	}

	@Test
	@DisplayName("La propuesta recoge la accion que el enunciado contiene")
	void derivadaDelEnunciado() {
		List<Suggestion> ps = sugeridor.proponer(CON_ACCION, false);

		assertThat(ps.get(0).texto()).contains("registrar");
		assertThat(ps.get(0).texto()).contains("una mascota");
	}

	@Test
	@DisplayName("Propone tambien el caso adverso, no solo el favorable")
	void casoAdverso() {
		assertThat(sugeridor.proponer(CON_ACCION, false))
				.anyMatch(p -> p.texto().contains("invalidos") || p.texto().contains("rechaza"));
	}

	@Test
	@DisplayName("ANA-18: no inventa magnitudes; deja el hueco marcado")
	void sinInventarMagnitudes() {
		List<Suggestion> ps = sugeridor.proponer(
				"El sistema debera responder a las consultas de busqueda.", true);

		Suggestion conMagnitud = ps.stream().filter(Suggestion::exigeDecision).findFirst().orElseThrow();

		assertThat(conMagnitud.texto()).contains(RuleBasedCriterionSuggester.HUECO);
		assertThat(conMagnitud.texto()).doesNotMatch(".*\\b\\d+\\s*(segundos?|ms|%|milisegundos?)\\b.*");
	}

	@Test
	@DisplayName("Sin magnitud exigida, ninguna propuesta contiene huecos")
	void sinHuecosCuandoNoProceden() {
		assertThat(sugeridor.proponer(CON_ACCION, false))
				.noneMatch(p -> p.texto().contains(RuleBasedCriterionSuggester.HUECO));
		assertThat(sugeridor.proponer(CON_ACCION, false)).noneMatch(Suggestion::exigeDecision);
	}

	@Test
	@DisplayName("Ante un enunciado sin accion observable no propone nada")
	void silencioAnteLoVago() {
		assertThat(sugeridor.proponer(
				"El sistema debera gestionar adecuadamente la interoperabilidad.", false)).isEmpty();
	}

	@Test
	@DisplayName("Un enunciado vacio no produce propuesta")
	void enunciadoVacio() {
		assertThat(sugeridor.proponer("", false)).isEmpty();
		assertThat(sugeridor.proponer(null, false)).isEmpty();
	}

	@Test
	@DisplayName("Cada propuesta explica de donde sale")
	void fundamentoPresente() {
		for (Suggestion p : sugeridor.proponer(CON_ACCION, true)) {
			assertThat(p.fundamento()).isNotBlank();
		}
	}

	@Test
	@DisplayName("El objeto se corta antes de la enumeracion, para que la frase se sostenga")
	void objetoAcotado() {
		Suggestion p = sugeridor.proponer(CON_ACCION, false).get(0);

		assertThat(p.texto()).doesNotContain("ingresando");
		assertThat(p.texto()).doesNotContain("especie");
	}

	@Test
	@DisplayName("Los acentos del enunciado no impiden reconocer la accion")
	void acentosIndiferentes() {
		assertThat(sugeridor.proponer(
				"El sistema deberá registrar la información médica.", false)).isNotEmpty();
	}
}
