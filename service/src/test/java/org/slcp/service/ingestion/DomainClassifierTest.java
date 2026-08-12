package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.ingestion.DomainClassifier.Reparto;

/**
 * Oraculo del reparto por dominio.
 *
 * <p>Los enunciados provienen del archivo de prueba con diez asuntos mezclados,
 * transversales y repetidos, que es el caso que motivo esta separacion.</p>
 */
class DomainClassifierTest {

	private static final List<String> PROYECTO = List.of(
			"El sistema debera registrar una parcela de cultivo con su identificador, superficie en hectareas, tipo de suelo y cultivo sembrado.",
			"El sistema debera dar de alta un sensor asociandolo a una parcela, con su tipo de medida y su periodo de muestreo.",
			"El sistema debera almacenar cada lectura recibida de un sensor, con el identificador del sensor, el valor y la marca de tiempo.",
			"Cuando la humedad media de una parcela descienda por debajo del umbral configurado para su cultivo, el sistema debera activar la valvula de riego.",
			"El sistema debera exportar el historial de riego de una explotacion en un archivo de valores separados por comas.");

	private static final String PROPIO =
			"El sistema debera registrar la calibracion de un sensor de humedad del suelo instalado en una parcela.";

	private static final String REPETIDO =
			"La plataforma registrara cada parcela de cultivo indicando su identificador, la superficie en hectareas, la clase de suelo y el cultivo sembrado.";

	private static final String AJENO =
			"El sistema debera registrar la consulta medica con el diagnostico, el tratamiento prescrito y la fecha de la proxima cita del paciente.";

	private static final String TRANSVERSAL =
			"El sistema debera exigir un segundo factor de verificacion al iniciar sesion cuando el usuario acceda desde un dispositivo no reconocido.";

	@Test
	@DisplayName("Un requisito del asunto del proyecto entra")
	void propioEntra() {
		Reparto r = DomainClassifier.repartir(PROYECTO, List.of(PROPIO));

		assertThat(r.propios()).containsExactly(0);
		assertThat(r.ajenos()).isEmpty();
	}

	@Test
	@DisplayName("Un repetido con otras palabras sigue siendo del proyecto")
	void repetidoEsPropio() {
		// Se deja pasar a proposito: el filtro es de dominio, y de la repeticion se
		// ocupa despues la comparacion por narrativa. Retenerlo aqui lo sacaria de
		// esa comprobacion y acabaria entrando duplicado.
		Reparto r = DomainClassifier.repartir(PROYECTO, List.of(REPETIDO));

		assertThat(r.propios()).containsExactly(0);
	}

	@Test
	@DisplayName("Un requisito de otro asunto queda retenido, no importado")
	void ajenoSeRetiene() {
		Reparto r = DomainClassifier.repartir(PROYECTO, List.of(AJENO));

		assertThat(r.propios()).isEmpty();
		assertThat(r.ajenos()).hasSize(1);
		assertThat(r.ajenos().get(0).indices()).containsExactly(0);
	}

	@Test
	@DisplayName("Lo que vale para cualquier sistema se separa aparte")
	void transversalAparte() {
		Reparto r = DomainClassifier.repartir(PROYECTO, List.of(TRANSVERSAL));

		assertThat(r.transversales()).hasSize(1);
		assertThat(r.propios()).isEmpty();
		assertThat(r.ajenos()).isEmpty();
	}

	@Test
	@DisplayName("Los ajenos del mismo asunto quedan en el mismo grupo")
	void agrupaPorAsunto() {
		Reparto r = DomainClassifier.repartir(PROYECTO, List.of(
				"El sistema debera registrar la consulta medica del paciente con su diagnostico y su tratamiento.",
				"El sistema debera advertir al medico de las alergias del paciente antes de confirmar la prescripcion del tratamiento.",
				"El sistema debera reservar una mesa del restaurante para la fecha y el numero de comensales que indique el cliente."));

		// Los dos de historia clinica juntos, el de restaurante aparte.
		assertThat(r.ajenos()).hasSize(2);
		assertThat(r.ajenos().get(0).indices()).hasSize(2);
	}

	@Test
	@DisplayName("El grupo se etiqueta con lo que sus requisitos comparten")
	void etiquetaConTerminos() {
		Reparto r = DomainClassifier.repartir(PROYECTO, List.of(
				"El sistema debera registrar la consulta medica del paciente con su diagnostico.",
				"El sistema debera advertir al medico de las alergias del paciente antes de prescribir."));

		assertThat(r.ajenos().get(0).etiqueta()).contains("paciente");
	}

	@Test
	@DisplayName("Sin proyecto con que comparar, todo entra")
	void primerDocumentoTodoEntra() {
		// El primer documento define el dominio y no puede ser ajeno a si mismo.
		Reparto r = DomainClassifier.repartir(List.of(), List.of(AJENO, TRANSVERSAL, PROPIO));

		assertThat(r.propios()).hasSize(3);
		assertThat(r.ajenos()).isEmpty();
	}

	@Test
	@DisplayName("Una mezcla se reparte en las tres clases")
	void mezclaCompleta() {
		Reparto r = DomainClassifier.repartir(PROYECTO,
				List.of(PROPIO, AJENO, TRANSVERSAL, REPETIDO));

		assertThat(r.propios()).containsExactly(0, 3);
		assertThat(r.transversales()).hasSize(1);
		assertThat(r.ajenos()).hasSize(1);
	}
}
