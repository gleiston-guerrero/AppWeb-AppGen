package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.generation.ActorExtractor.Identificado;

/**
 * Oraculo de la identificacion de actores.
 *
 * <p>Vigila la distincion que da sentido a todo esto: el actor de un caso de uso
 * no es el interesado que trae la especificacion, y el sistema no es un actor.</p>
 */
class ActorExtractorTest {

	@Test
	@DisplayName("El sujeto de la obligacion es el actor cuando no es el sistema")
	void sujetoEsActor() {
		List<Identificado> is = ActorExtractor.identificar(
				"El operario de campo debera calibrar el sensor y registrar su desviacion.");

		assertThat(is).hasSize(1);
		assertThat(is.get(0).actor()).isEqualTo("Operario de campo");
		assertThat(is.get(0).seguro()).isTrue();
	}

	@Test
	@DisplayName("El sistema no es un actor: es la frontera de lo que se dibuja")
	void elSistemaNoEsActor() {
		// Casi todos los enunciados lo ponen como sujeto. Tomarlo daria un diagrama
		// con un unico actor llamado sistema y ningun caso de uso util.
		List<Identificado> is = ActorExtractor.identificar(
				"El sistema debera registrar una parcela de cultivo con su superficie.");

		assertThat(is).hasSize(1);
		assertThat(is.get(0).actor()).isEqualTo(ActorExtractor.SIN_IDENTIFICAR);
		assertThat(is.get(0).seguro()).isFalse();
	}

	@Test
	@DisplayName("Se reconocen tambien plataforma, aplicacion y servicio como el sistema")
	void otrosNombresDelSistema() {
		for (String nombre : List.of("plataforma", "aplicacion", "servicio")) {
			List<Identificado> is = ActorExtractor.identificar(
					"La " + nombre + " debera registrar la parcela.");

			assertThat(is.get(0).seguro())
					.as("'%s' no deberia tomarse por actor", nombre)
					.isFalse();
		}
	}

	@Test
	@DisplayName("Cuando el sistema dirige algo a alguien, ese alguien es el actor")
	void destinatarioEsActor() {
		List<Identificado> is = ActorExtractor.identificar(
				"El sistema debera notificar al responsable de la explotacion cuando un sensor "
						+ "deje de enviar lecturas.");

		assertThat(is).extracting(Identificado::actor).contains("Responsable de la explotacion");
		assertThat(is.get(0).seguro()).isTrue();
	}

	@Test
	@DisplayName("Quien indica o elige algo tambien interviene")
	void quienIndicaEsActor() {
		List<Identificado> is = ActorExtractor.identificar(
				"El sistema debera mostrar el consumo acumulado en el periodo que indique el "
						+ "responsable de la explotacion.");

		assertThat(is).extracting(Identificado::actor).contains("Responsable de la explotacion");
	}

	@Test
	@DisplayName("El objeto de la accion no se confunde con el actor")
	void objetoNoEsActor() {
		// "mostrar el consumo" tiene objeto, no destinatario: sin la preposicion no
		// hay a quien dirigirlo.
		List<Identificado> is = ActorExtractor.identificar(
				"El sistema debera mostrar el consumo de agua acumulado de cada parcela.");

		assertThat(is.get(0).actor()).isEqualTo(ActorExtractor.SIN_IDENTIFICAR);
	}

	@Test
	@DisplayName("El infinitivo que sigue al actor no forma parte de su nombre")
	void sinInfinitivo() {
		List<Identificado> is = ActorExtractor.identificar(
				"El sistema debera permitir al socio reservar un titulo prestado.");

		assertThat(is).extracting(Identificado::actor).contains("Socio");
	}

	@Test
	@DisplayName("Cuando no puede identificarse, se dice: no se inventa")
	void noSeInventa() {
		// Averiguarlo exige conocer el dominio, y eso lo decide una persona o un
		// modelo que lo conozca. Poner uno cualquiera pareceria conocimiento.
		List<Identificado> is = ActorExtractor.identificar(
				"El sistema debera exportar el historial de riego en un archivo.");

		assertThat(is.get(0).actor()).isEqualTo(ActorExtractor.SIN_IDENTIFICAR);
		assertThat(is.get(0).porque()).contains("conocer el dominio");
		assertThat(ActorExtractor.tieneActor("El sistema debera exportar el historial.")).isFalse();
	}

	@Test
	@DisplayName("Las tildes no cortan el nombre del actor")
	void tildesEnteras() {
		List<Identificado> is = ActorExtractor.identificar(
				"El responsable de la explotación deberá aprobar el plan de riego.");

		assertThat(is.get(0).actor()).isEqualTo("Responsable de la explotación");
	}
}
