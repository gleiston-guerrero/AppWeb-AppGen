package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de la deteccion de dominio ajeno. */
class DomainCoherenceTest {

	private static final List<String> GRANJA = List.of(
			"El sistema debera registrar una parcela de cultivo con su superficie y tipo de suelo.",
			"El sistema debera dar de alta un sensor asociandolo a una parcela con su periodo de muestreo.",
			"El sistema debera almacenar cada lectura recibida de un sensor con su marca de tiempo.",
			"Cuando la humedad de una parcela baje del umbral de su cultivo, se activara el riego.",
			"El sistema debera mostrar el consumo de agua acumulado de cada parcela.");

	private static final List<String> GANADO = List.of(
			"El sistema debera registrar el pesaje de un animal con su crotal y la fecha.",
			"Cuando la temperatura del invernadero salga del rango del cultivo, se notificara al responsable.",
			"El sistema debera almacenar las lecturas de temperatura del invernadero con su marca de tiempo.",
			"El sistema debera exportar el historial de riego de la explotacion.",
			"El sistema debera dar de baja un sensor conservando sus lecturas anteriores.");

	private static final List<String> CLASES = List.of(
			"El sistema debera registrar la asistencia de cada estudiante a la clase.",
			"El docente debera publicar material de estudio asociado a una unidad del programa.",
			"El sistema debera calificar los cuestionarios de opcion multiple del estudiante.",
			"El sistema debera mostrar al docente el avance de cada estudiante en el curso.",
			"El estudiante debera entregar sus trabajos antes de la fecha limite.");

	@Test
	@DisplayName("Dos documentos del mismo sistema no producen aviso")
	void mismoDominioSinAviso() {
		DomainCoherence.Veredicto v = DomainCoherence.examinar(GRANJA, GANADO);

		assertThat(v.aviso()).isFalse();
		assertThat(v.coincidencia()).isGreaterThan(DomainCoherence.UMBRAL_DOMINIO);
	}

	@Test
	@DisplayName("Un documento de otro asunto produce aviso")
	void dominioAjenoAvisa() {
		DomainCoherence.Veredicto v = DomainCoherence.examinar(GRANJA, CLASES);

		assertThat(v.aviso()).isTrue();
		assertThat(v.explicacion()).contains("otro sistema");
	}

	@Test
	@DisplayName("El aviso es simetrico: importa lo ajenos que son, no cual llega")
	void simetrico() {
		assertThat(DomainCoherence.examinar(CLASES, GRANJA).aviso()).isTrue();
		assertThat(DomainCoherence.examinar(GRANJA, CLASES).aviso()).isTrue();
	}

	@Test
	@DisplayName("Con pocos requisitos no se juzga el dominio")
	void pocosNoSeJuzgan() {
		// Un proyecto que empieza no se parece a nada, y avisar entonces seria
		// avisar siempre en el primer documento.
		DomainCoherence.Veredicto v = DomainCoherence.examinar(
				GRANJA.subList(0, 2), CLASES);

		assertThat(v.aviso()).isFalse();
		assertThat(v.explicacion()).contains("No hay bastantes");
	}

	@Test
	@DisplayName("Un requisito suelto de otro asunto se advierte")
	void requisitoAjenoSuelto() {
		DomainCoherence.Veredicto v = DomainCoherence.examinarUno(GRANJA,
				"El estudiante debera entregar sus trabajos antes de la fecha limite establecida.");

		assertThat(v.aviso()).isTrue();
	}

	@Test
	@DisplayName("Un requisito del mismo asunto no se advierte")
	void requisitoPropioSuelto() {
		DomainCoherence.Veredicto v = DomainCoherence.examinarUno(GRANJA,
				"El sistema debera registrar la humedad de la parcela medida por su sensor.");

		assertThat(v.aviso()).isFalse();
	}

	@Test
	@DisplayName("El aviso enumera lo que comparte y lo que trae de nuevo")
	void enumeraTerminos() {
		DomainCoherence.Veredicto v = DomainCoherence.examinar(GRANJA, CLASES);

		assertThat(v.terminosDeLoQueLlega()).isNotEmpty();
		assertThat(v.terminosDeLoQueLlega().size()).isLessThanOrEqualTo(12);
	}

	@Test
	@DisplayName("Un conjunto consigo mismo coincide del todo")
	void consigoMismo() {
		DomainCoherence.Veredicto v = DomainCoherence.examinar(GRANJA, GRANJA);

		assertThat(v.aviso()).isFalse();
		assertThat(v.coincidencia()).isEqualTo(1.0);
	}
}
