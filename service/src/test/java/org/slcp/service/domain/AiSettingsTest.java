package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de la configuracion del servicio de IA. */
class AiSettingsTest {

	private static final Instant T0 = Instant.parse("2026-08-12T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	/** Se prueba sobre una funcion cualquiera: el comportamiento es el mismo en todas. */
	private AiSettings nueva() {
		return AiSettings.inicial(PROYECTO, AiFeature.GENERATE_TESTS, QUIEN, T0);
	}

	@Test
	@DisplayName("Nace desactivada y sin credencial")
	void naceDesactivada() {
		AiSettings s = nueva();

		assertThat(s.isEnabled()).isFalse();
		assertThat(s.tieneCredencial()).isFalse();
	}

	@Test
	@DisplayName("No puede activarse sin credencial")
	void noActivaSinCredencial() {
		// Quedaria activa de nombre y fallaria en cada generacion, y ese fallo se
		// leeria como que el modelo no sirve.
		assertThatThrownBy(() -> nueva().activar(true, T0, QUIEN))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("sin credencial");
	}

	@Test
	@DisplayName("Con credencial si puede activarse")
	void activaConCredencial() {
		AiSettings s = nueva();
		s.guardarCredencial("cifrada", "…2b7a", T0, QUIEN);
		s.activar(true, T0, QUIEN);

		assertThat(s.isEnabled()).isTrue();
	}

	@Test
	@DisplayName("Cambiar de proveedor retira la credencial y desactiva")
	void cambiarProveedorRetira() {
		// Una clave de un servicio no vale en otro, y conservarla haria que la
		// plataforma la enviase a un tercero distinto del que la emitio.
		AiSettings s = nueva();
		s.guardarCredencial("cifrada", "…2b7a", T0, QUIEN);
		s.activar(true, T0, QUIEN);

		s.configurar(AiProvider.OPENAI, null, null, T0, QUIEN);

		assertThat(s.tieneCredencial()).isFalse();
		assertThat(s.isEnabled()).isFalse();
		assertThat(s.getProvider()).isEqualTo(AiProvider.OPENAI);
	}

	@Test
	@DisplayName("Al cambiar de proveedor se toman su modelo y direccion habituales")
	void valoresPorDefectoDelProveedor() {
		AiSettings s = nueva();
		s.configurar(AiProvider.OPENAI, null, null, T0, QUIEN);

		assertThat(s.getModel()).isEqualTo(AiProvider.OPENAI.getModeloPorDefecto());
		assertThat(s.getBaseUrl()).isEqualTo(AiProvider.OPENAI.getDireccionPorDefecto());
	}

	@Test
	@DisplayName("Cambiar solo el modelo conserva la credencial")
	void cambiarModeloConserva() {
		// Corregir el nombre del modelo no debe obligar a teclear la clave otra vez.
		AiSettings s = nueva();
		s.guardarCredencial("cifrada", "…2b7a", T0, QUIEN);
		s.configurar(null, "otro-modelo", null, T0, QUIEN);

		assertThat(s.tieneCredencial()).isTrue();
		assertThat(s.getModel()).isEqualTo("otro-modelo");
	}

	@Test
	@DisplayName("Retirar la credencial desactiva el servicio")
	void retirarDesactiva() {
		AiSettings s = nueva();
		s.guardarCredencial("cifrada", "…2b7a", T0, QUIEN);
		s.activar(true, T0, QUIEN);

		s.retirarCredencial(T0, QUIEN);

		assertThat(s.tieneCredencial()).isFalse();
		assertThat(s.isEnabled()).isFalse();
	}
}
