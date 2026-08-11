package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.ingestion.CriterionSuggester.Suggestion;

/** Oraculo del sugeridor de redaccion. */
class StatementSuggesterTest {

	private static final String REGLAS = """
			verb.binding = debera, deberan, debe, deben
			verb.preference = deberia, deberian
			verb.permission = podra, podran, puede
			vague = rapido, rapida, amigable, adecuado, eficiente
			forbidden.capability = debera ser capaz de, debera poder
			forbidden.passive = se requiere que, debera ser generado
			forbidden.negative = no debera
			conjunction = ademas, y debera
			subject = el sistema, la plataforma
			max.words = 40
			""";

	private StatementSuggester sugeridor() throws IOException {
		return new StatementSuggester(RequirementLinter.cargar(new StringReader(REGLAS)));
	}

	private List<Suggestion> proponer(String enunciado) throws IOException {
		return sugeridor().proponer(enunciado);
	}

	@Test
	@DisplayName("Un enunciado conforme no recibe propuesta")
	void conformeSinPropuesta() throws IOException {
		assertThat(proponer("El sistema debera registrar la mascota con nombre y especie.")).isEmpty();
	}

	@Test
	@DisplayName("Conforme: la preferencia pasa a obligacion")
	void preferenciaAObligacion() throws IOException {
		List<Suggestion> ps = proponer("El sistema deberia mostrar el panel.");

		assertThat(ps).isNotEmpty();
		assertThat(ps.get(0).texto()).contains("debera").doesNotContain("deberia");
	}

	@Test
	@DisplayName("Conforme: la capacidad pasa a accion")
	void capacidadAAccion() throws IOException {
		assertThat(proponer("El sistema debera ser capaz de generar informes.").get(0).texto())
				.isEqualTo("El sistema debera generar informes.");
	}

	@Test
	@DisplayName("ANA-18: los terminos sin magnitud quedan como hueco, nunca como cifra")
	void huecoEnLugarDeCifra() throws IOException {
		Suggestion p = proponer("El sistema debera mostrar un panel rapido y amigable.").get(0);

		assertThat(p.texto()).contains(StatementSuggester.HUECO);
		assertThat(p.texto()).doesNotContain("rapido").doesNotContain("amigable");
		assertThat(p.exigeDecision()).isTrue();
	}

	@Test
	@DisplayName("No ambiguo: la pasiva pasa a activa con el participio en infinitivo")
	void pasivaAActiva() throws IOException {
		String texto = proponer(
				"Se requiere que sea notificado el responsable cuando un animal salga.").get(0).texto();

		assertThat(texto).startsWith("El sistema debera notificar");
		// Un participio en la frase resultante indicaria que falta el verbo principal
		assertThat(texto).doesNotContain("debera notificado");
	}

	@Test
	@DisplayName("El pronombre del verbo se retira: el sistema no se aplica la accion a si mismo")
	void sinEncliticoAlAnteponerSujeto() throws IOException {
		String texto = proponer("Debera almacenarse el historial de riego.").get(0).texto();

		assertThat(texto).isEqualTo("El sistema debera almacenar el historial de riego.");
		assertThat(texto).doesNotContain("almacenarse");
	}

	@Test
	@DisplayName("Singular: el enunciado con dos obligaciones se divide, y la segunda consta")
	void divisionDeObligaciones() throws IOException {
		List<Suggestion> ps = proponer(
				"El sistema debera registrar la cabeza de ganado, y ademas debera notificar al veterinario.");

		Suggestion division = ps.stream()
				.filter(p -> p.fundamento().contains("dos obligaciones")).findFirst().orElseThrow();

		assertThat(division.texto()).contains("registrar").doesNotContain("notificar");
		assertThat(division.fundamento()).contains("notificar al veterinario");
	}

	@Test
	@DisplayName("La condicion se lleva al principio sin perder ninguna letra")
	void condicionAlPrincipioIntacta() throws IOException {
		List<Suggestion> ps = proponer(
				"Se requiere que sea notificado el responsable cuando un animal salga del perimetro.");

		Suggestion ordenada = ps.stream()
				.filter(p -> p.texto().startsWith("Cuando")).findFirst().orElseThrow();

		// El defecto que esta prueba vigila: avanzar de mas al cortar se comia la
		// primera letra de lo que seguia, y "un animal" quedaba en "n animal". La
		// comprobacion mira el comienzo y no la ausencia de una subcadena: "n
		// animal" esta contenido en "un animal", de modo que buscarlo como
		// subcadena no distingue el caso bueno del malo.
		assertThat(ordenada.texto()).startsWith("Cuando un animal salga");
	}

	@Test
	@DisplayName("ANA-20: cuando hay varias correcciones posibles, se ofrece mas de una")
	void variasOpciones() throws IOException {
		assertThat(proponer(
				"Se requiere que sea notificado el responsable cuando un animal salga del perimetro."))
				.hasSizeGreaterThan(1);
	}

	@Test
	@DisplayName("Cada propuesta explica que cambio y por que")
	void fundamentoPresente() throws IOException {
		for (Suggestion p : proponer("El sistema deberia mostrar un panel rapido.")) {
			assertThat(p.fundamento()).isNotBlank();
			assertThat(p.fundamento().length()).isGreaterThan(20);
		}
	}

	@Test
	@DisplayName("Ninguna propuesta introduce una cifra que el enunciado no traia")
	void sinCifrasInventadas() throws IOException {
		for (Suggestion p : proponer("El sistema debera responder de forma rapida.")) {
			assertThat(p.texto()).doesNotMatch(".*\\b\\d+\\s*(segundos?|ms|%|milisegundos?)\\b.*");
		}
	}

	@Test
	@DisplayName("Un enunciado vacio no produce propuesta")
	void enunciadoVacio() throws IOException {
		assertThat(proponer("")).isEmpty();
		assertThat(proponer(null)).isEmpty();
	}
}
