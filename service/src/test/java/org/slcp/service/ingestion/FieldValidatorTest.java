package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.ingestion.FieldValidator.Reparo;

/**
 * Oraculo de la comprobacion de campos importados.
 *
 * <p>Vigila el principio que la motiva: que un valor venga en el archivo no
 * significa que sea correcto.</p>
 */
class FieldValidatorTest {

	private List<Reparo> revisar(String nombre, String enunciado, String criterio,
			String interesado, String prioridad, String id) {
		return FieldValidator.revisar(nombre, enunciado, criterio, interesado, prioridad, id);
	}

	@Test
	@DisplayName("Un requisito bien formado no produce ningun reparo")
	void bienFormadoPasa() {
		assertThat(revisar("Registrar parcela de cultivo",
				"El sistema debera registrar una parcela de cultivo con su superficie.",
				"Con datos validos, registrar una parcela y comprobar que aparece en el listado.",
				"Responsable de la explotacion", "Must", "RF-01")).isEmpty();
	}

	@Test
	@DisplayName("Un aparato es una fuente legitima: no se exige que sea una persona")
	void aparatoEsFuenteValida() {
		// Un sensor o un sistema externo pueden ser el origen de los datos.
		assertThat(revisar("Almacenar lecturas",
				"El sistema debera almacenar cada lectura recibida de un sensor.",
				"Enviar una lectura y comprobar que queda almacenada con su marca de tiempo.",
				"Sensor de humedad", "Must", "RF-03")).isEmpty();
	}

	@Test
	@DisplayName("Un criterio que repite el enunciado es un reparo grave")
	void criterioQueRepite() {
		// Parece que el requisito esta verificado, y de el saldrian pruebas que no
		// comprueban nada.
		String enunciado = "El sistema debera exportar el historial de riego.";
		List<Reparo> rs = revisar("Exportar", enunciado, enunciado, "Responsable", "Must", "RF-08");

		assertThat(rs).hasSize(1);
		assertThat(rs.get(0).campo()).isEqualTo("verification");
		assertThat(rs.get(0).grave()).isTrue();
	}

	@Test
	@DisplayName("Un criterio casi igual con otras palabras tambien se senala")
	void criterioCasiIgual() {
		List<Reparo> rs = revisar("Registrar parcela",
				"El sistema debera registrar una parcela de cultivo con su superficie y su tipo de suelo.",
				"El sistema registrara una parcela de cultivo con superficie y tipo de suelo.",
				"Responsable", "Must", "RF-01");

		assertThat(rs).extracting(Reparo::campo).contains("verification");
		assertThat(rs.get(0).grave()).isFalse();
	}

	@Test
	@DisplayName("Un interesado que es una oracion se senala como grave")
	void interesadoQueEsOracion() {
		List<Reparo> rs = revisar("Consultar", "El sistema debera mostrar el consumo.",
				"Consultar el periodo y comprobar la suma.",
				"El responsable de la explotacion es quien lo pidio", "Must", "RF-06");

		assertThat(rs).extracting(Reparo::campo).contains("actor");
		assertThat(rs.get(0).grave()).isTrue();
	}

	@Test
	@DisplayName("Una prioridad no reconocida se senala")
	void prioridadInventada() {
		List<Reparo> rs = revisar("Alta de sensor", "El sistema debera dar de alta un sensor.",
				"Dar de alta y comprobar que queda vinculado a su parcela.",
				"Responsable", "Urgentisimo", "RF-02");

		assertThat(rs).extracting(Reparo::campo).contains("priority");
	}

	@Test
	@DisplayName("Un identificador no funcional con enunciado funcional se senala")
	void tipoContradictorio() {
		// "RNF-T6" tiene el numero tras una letra: el prefijo es RNF, no RNF-T.
		List<Reparo> rs = revisar("Exportar datos personales",
				"El sistema debera exportar los datos personales del usuario que lo solicite.",
				"Solicitar la exportacion y comprobar el archivo entregado.",
				"Usuario", "Must", "RNF-T6");

		assertThat(rs).extracting(Reparo::campo).contains("kind");
	}

	@Test
	@DisplayName("Un nombre igual que el enunciado se senala")
	void nombreQueRepite() {
		String enunciado = "El sistema debera dar de alta un sensor.";
		List<Reparo> rs = revisar(enunciado, enunciado,
				"Dar de alta y comprobar que queda vinculado a su parcela.",
				"Responsable", "Must", "RF-02");

		assertThat(rs).extracting(Reparo::campo).contains("nombre");
	}

	@Test
	@DisplayName("Un criterio demasiado corto no puede describir una comprobacion")
	void criterioCorto() {
		List<Reparo> rs = revisar("Cerrar riego",
				"El sistema debera cerrar la valvula al alcanzar la dosis.",
				"Se comprueba.", "Responsable", "Must", "RF-05");

		assertThat(rs).extracting(Reparo::campo).contains("verification");
	}

	@Test
	@DisplayName("Los campos ausentes no producen reparos: faltar no es estar mal")
	void ausentesNoSonReparo() {
		// La ausencia ya la cuenta el informe de importacion por su lado.
		assertThat(revisar(null, "El sistema debera registrar una parcela.", null, null, null, null))
				.isEmpty();
	}

	@Test
	@DisplayName("Nada se corrige ni se descarta: solo se senala")
	void soloSenala() {
		// Corregir supondria decidir que quiso decir quien lo escribio, y descartar
		// perderia un dato que quiza solo esta mal escrito.
		String enunciado = "El sistema debera exportar el historial.";
		List<Reparo> rs = revisar("Exportar", enunciado, enunciado, "Responsable", "Must", "RF-08");

		assertThat(rs.get(0).valor()).isEqualTo(enunciado);
	}
}
