package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la prueba de aceptacion.
 *
 * <p>Un criterio de verificacion bien escrito ya trae las tres partes de un
 * escenario --- contexto, accion y resultado ---, de modo que traducirlo consiste
 * en leerlas. Pegarlo entero en el resultado produce un "Entonces" que repite la
 * accion y esconde lo unico que se comprueba.</p>
 */
class AcceptanceScenarioTest {

	private final DerivedTestGenerator generador = new DerivedTestGenerator();

	private String escenarioDe(String nombre, String enunciado, String criterio) {
		return generador.generar(
				new RequirementInput("REQ-0001-v1", "RF-01", "FUNCTIONAL", nombre, enunciado,
						criterio, null),
				DerivedTestGenerator.ACEPTACION).get(0).content();
	}

	private static final String ENUNCIADO =
			"El sistema debera registrar una parcela de cultivo con su superficie.";

	private static final String CRITERIO =
			"Con datos validos, registrar una parcela y comprobar que aparece en el listado.";

	@Test
	@DisplayName("El contexto del criterio va al Dado, no al resultado")
	void contextoAlDado() {
		String e = escenarioDe("Registrar parcela", ENUNCIADO, CRITERIO);

		assertThat(e).contains("Dado que se parte de datos validos");
	}

	@Test
	@DisplayName("La accion del criterio va al Cuando")
	void accionAlCuando() {
		String e = escenarioDe("Registrar parcela", ENUNCIADO, CRITERIO);

		assertThat(e).contains("Cuando se registra una parcela");
	}

	@Test
	@DisplayName("El Entonces afirma solo lo observable")
	void resultadoAlEntonces() {
		String e = escenarioDe("Registrar parcela", ENUNCIADO, CRITERIO);

		assertThat(e).contains("Entonces aparece en el listado");
		// Lo que suele colarse: el criterio entero pegado tras "Entonces".
		assertThat(e).doesNotContain("Entonces Con datos validos");
		assertThat(e).doesNotContain("Entonces comprobar");
	}

	@Test
	@DisplayName("La accion no se repite en el resultado")
	void sinRepetirLaAccion() {
		String e = escenarioDe("Registrar parcela", ENUNCIADO, CRITERIO);

		String entonces = e.substring(e.indexOf("Entonces"));
		assertThat(entonces).doesNotContain("registrar una parcela");
	}

	@Test
	@DisplayName("El verbo se conjuga: el escenario se lee en presente, no en infinitivo")
	void verboEnPresente() {
		String e = escenarioDe("Registrar parcela", ENUNCIADO, CRITERIO);

		assertThat(e).doesNotContain("Cuando se registrar");
	}

	@Test
	@DisplayName("El verbo concuerda en numero con su complemento")
	void concordanciaDeNumero() {
		// En la pasiva refleja el verbo concuerda: "se envian lecturas".
		String e = escenarioDe("Activar riego",
				"Cuando la humedad descienda del umbral, el sistema debera activar la valvula.",
				"Con un umbral del 30 por ciento, enviar lecturas y comprobar que se emite la orden.");

		// Con tilde: los verbos en -iar la llevan en la tercera persona, y sin ella
		// "envia" se leeria como otro tiempo.
		assertThat(e).contains("Cuando se envían lecturas");
	}

	@Test
	@DisplayName("Sin contexto en el criterio, el Dado remite a las condiciones del requisito")
	void contextoPorDefecto() {
		String e = escenarioDe("Almacenar lecturas",
				"El sistema debera almacenar cada lectura recibida de un sensor.",
				"Enviar una lectura y comprobar que queda almacenada con su marca de tiempo.");

		// Se redacta para encajar tras "se parte de": sin ello saldria "de el sistema".
		assertThat(e).contains("Dado que se parte de las condiciones que exige RF-01");
		assertThat(e).doesNotContain("de el sistema");
	}

	@Test
	@DisplayName("Sin criterio, el resultado esperado queda como hueco")
	void sinCriterioHueco() {
		String e = escenarioDe("Exportar historial",
				"El sistema debera exportar el historial de riego.", null);

		assertThat(e).contains("Entonces " + DerivedTestGenerator.HUECO);
	}

	@Test
	@DisplayName("El titulo del escenario no repite la caracteristica")
	void tituloDistinto() {
		// Si ambos dicen lo mismo, al anadir un segundo escenario no habria como
		// distinguirlos.
		String e = escenarioDe("Registrar parcela", ENUNCIADO, CRITERIO);

		assertThat(e).contains("Caracteristica: Registrar parcela");
		assertThat(e).doesNotContain("Escenario: Registrar parcela\n");
	}

	@Test
	@DisplayName("Varias afirmaciones encadenadas se separan en lineas")
	void afirmacionesSeparadas() {
		// Una por linea: si la prueba falla, se sabe cual fallo.
		String e = escenarioDe("Dar de alta sensor",
				"El sistema debera dar de alta un sensor asociandolo a una parcela.",
				"Dar de alta un sensor y comprobar que queda vinculado a su parcela y que consta "
						+ "su periodo de muestreo.");

		assertThat(e).contains("Entonces queda vinculado a su parcela");
		assertThat(e).contains("Y consta su periodo de muestreo");
	}
}
