package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.ingestion.RequirementLinter.Caracteristica;
import org.slcp.service.ingestion.RequirementLinter.Gravedad;
import org.slcp.service.ingestion.RequirementLinter.Hallazgo;

/**
 * Oraculo del validador de redaccion.
 *
 * <p>Cada prueba comprueba una regla de ISO/IEC/IEEE 29148 con un enunciado que
 * la incumple y, la ultima, con uno que la cumple. Sin ese caso en positivo, un
 * validador que reportase siempre pasaria todas las demas.</p>
 */
class RequirementLinterTest {

	private static final String REGLAS = """
			verb.binding = debera, deberan, debe, deben
			verb.preference = deberia, deberian
			verb.permission = podra, podran, puede
			vague = rapido, rapida, amigable, adecuado, eficiente
			forbidden.capability = debera ser capaz de, debera poder
			forbidden.passive = se requiere que, debera ser generado
			forbidden.negative = no debera, no deben
			conjunction = ademas, y debera
			subject = el sistema, la plataforma
			max.words = 30
			""";

	private RequirementLinter linter() throws Exception {
		return RequirementLinter.cargar(new StringReader(REGLAS));
	}

	private List<Hallazgo> examinar(String enunciado) throws Exception {
		return linter().examinar(enunciado);
	}

	private boolean hay(List<Hallazgo> hs, String regla) {
		return hs.stream().anyMatch(h -> h.regla().equals(regla));
	}

	@Test
	@DisplayName("Un enunciado conforme no produce hallazgo alguno")
	void enunciadoConforme() throws Exception {
		assertThat(examinar("El sistema debera registrar la mascota con nombre, especie y raza."))
				.isEmpty();
	}

	@Test
	@DisplayName("Conforme: una preferencia no es un requisito")
	void preferenciaNoEsRequisito() throws Exception {
		List<Hallazgo> hs = examinar("El sistema deberia permitir registrar una mascota.");

		assertThat(hay(hs, "verbo-no-vinculante")).isTrue();
		assertThat(hs.get(0).caracteristica()).isEqualTo(Caracteristica.CONFORME);
		assertThat(hs.get(0).gravedad()).isEqualTo(Gravedad.DEFECTO);
	}

	@Test
	@DisplayName("Conforme: una concesion tampoco obliga")
	void concesionNoObliga() throws Exception {
		assertThat(hay(examinar("El sistema podra enviar notificaciones."), "verbo-permisivo")).isTrue();
	}

	@Test
	@DisplayName("No ambiguo: la voz pasiva oculta quien actua")
	void vozPasiva() throws Exception {
		assertThat(hay(examinar("Se requiere que las notificaciones lleguen al propietario."),
				"voz-pasiva")).isTrue();
	}

	@Test
	@DisplayName("Conforme: poder hacer algo no es hacerlo")
	void capacidadNoEsAccion() throws Exception {
		assertThat(hay(examinar("El sistema debera ser capaz de generar informes."),
				"capacidad-en-lugar-de-accion")).isTrue();
	}

	@Test
	@DisplayName("Conforme: el enunciado negativo se se\u00f1ala como sospecha, no como defecto")
	void enunciadoNegativo() throws Exception {
		List<Hallazgo> hs = examinar("El sistema no debera permitir el acceso sin autenticar.");

		assertThat(hay(hs, "enunciado-negativo")).isTrue();
		assertThat(hs.stream().filter(h -> h.regla().equals("enunciado-negativo"))
				.findFirst().orElseThrow().gravedad()).isEqualTo(Gravedad.SOSPECHA);
	}

	@Test
	@DisplayName("Verificable: cada termino sin magnitud se reporta por separado")
	void terminosSinMagnitud() throws Exception {
		List<Hallazgo> hs = examinar(
				"El sistema debera responder de forma rapida y con una interfaz amigable.");

		assertThat(hs.stream().filter(h -> h.regla().equals("termino-sin-magnitud")).count())
				.isEqualTo(2);
		assertThat(hs.get(0).caracteristica()).isEqualTo(Caracteristica.VERIFICABLE);
	}

	@Test
	@DisplayName("Singular: dos obligaciones unidas se se\u00f1alan")
	void obligacionDoble() throws Exception {
		assertThat(hay(examinar(
				"El sistema debera registrar la mascota y ademas debera notificar al veterinario."),
				"posible-obligacion-doble")).isTrue();
	}

	@Test
	@DisplayName("No ambiguo: sin sujeto no se sabe quien actua")
	void sujetoAusente() throws Exception {
		assertThat(hay(examinar("Debera almacenarse el historial medico."), "sujeto-ausente")).isTrue();
	}

	@Test
	@DisplayName("Un enunciado ausente es un defecto de completitud, no de redaccion")
	void enunciadoAusente() throws Exception {
		List<Hallazgo> hs = examinar("   ");

		assertThat(hs).hasSize(1);
		assertThat(hs.get(0).caracteristica()).isEqualTo(Caracteristica.COMPLETO);
	}

	@Test
	@DisplayName("La busqueda es por palabra completa: 'facil' no aparece en 'facilitador'")
	void sinFalsosPositivos() throws Exception {
		RequirementLinter l = RequirementLinter.cargar(new StringReader("""
				verb.binding = debera
				vague = facil
				subject = el sistema
				max.words = 30
				"""));

		assertThat(l.examinar("El sistema debera notificar al facilitador del proyecto.")).isEmpty();
		assertThat(l.examinar("El sistema debera ser facil.")).isNotEmpty();
	}

	@Test
	@DisplayName("Los acentos no cambian el resultado")
	void acentosIndiferentes() throws Exception {
		assertThat(examinar("El sistema deberá responder de forma rápida."))
				.anyMatch(h -> h.regla().equals("termino-sin-magnitud"));
	}

	@Test
	@DisplayName("Singular: un enunciado muy extenso se se\u00f1ala")
	void enunciadoExtenso() throws Exception {
		String largo = "El sistema debera " + "registrar informacion diversa ".repeat(10) + ".";

		assertThat(hay(examinar(largo), "enunciado-extenso")).isTrue();
	}

	@Test
	@DisplayName("Cada hallazgo explica que corregir, no solo que esta mal")
	void explicacionesUtiles() throws Exception {
		for (Hallazgo h : examinar("El sistema deberia responder de forma rapida.")) {
			assertThat(h.explicacion()).isNotBlank();
			assertThat(h.explicacion().length()).isGreaterThan(30);
		}
	}

	@Test
	@DisplayName("Conforme distingue defecto de sospecha")
	void gravedadDiferenciada() throws Exception {
		assertThat(linter().conforme("El sistema debera registrar la mascota.")).isTrue();
		// Solo sospechas: sigue siendo conforme, porque exigen mirada humana
		assertThat(linter().conforme("El sistema no debera admitir duplicados.")).isTrue();
		// Defecto cierto
		assertThat(linter().conforme("El sistema deberia registrar la mascota.")).isFalse();
	}
}
