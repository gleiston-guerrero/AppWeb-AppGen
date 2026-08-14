package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la eleccion de proveedor por funcion.
 *
 * <p>Esta clase ya no custodia credenciales: viven en {@link AiCredential}, una
 * por proveedor, y varias conviven. Eso es lo que permite comparar cuatro
 * proveedores sin perder la clave de los otros tres al cambiar de uno.</p>
 */
class AiSettingsTest {

	private static final Instant T0 = Instant.parse("2026-08-13T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	/** Se prueba sobre una funcion cualquiera: el comportamiento es el mismo. */
	private AiSettings nueva() {
		return AiSettings.inicial(PROYECTO, AiFeature.GENERATE_TESTS, QUIEN, T0);
	}

	@Test
	@DisplayName("Nace desactivada")
	void naceDesactivada() {
		assertThat(nueva().isEnabled()).isFalse();
	}

	@Test
	@DisplayName("Elegir proveedor no arrastra ninguna credencial")
	void elegirProveedor() {
		// Las credenciales viven aparte: cambiar de proveedor aqui solo cambia a
		// cual se llama, y la clave del anterior sigue guardada por si se vuelve.
		AiSettings s = nueva();
		s.configurar(AiProvider.OPENAI, T0, QUIEN);

		assertThat(s.getProvider()).isEqualTo(AiProvider.OPENAI);
	}

	@Test
	@DisplayName("Cambiar de proveedor no desactiva la funcion")
	void cambiarNoDesactiva() {
		// Antes lo hacia, porque cambiar borraba la clave. Ya no la borra.
		AiSettings s = nueva();
		s.activar(true, T0, QUIEN);
		s.configurar(AiProvider.DEEPSEEK, T0, QUIEN);

		assertThat(s.isEnabled()).isTrue();
		assertThat(s.getProvider()).isEqualTo(AiProvider.DEEPSEEK);
	}

	@Test
	@DisplayName("Un proveedor nulo conserva el que hubiera")
	void nuloConserva() {
		AiSettings s = nueva();
		s.configurar(AiProvider.GOOGLE, T0, QUIEN);
		s.configurar(null, T0, QUIEN);

		assertThat(s.getProvider()).isEqualTo(AiProvider.GOOGLE);
	}

	@Test
	@DisplayName("Se activa y se desactiva, y consta quien lo hizo")
	void activarYDesactivar() {
		AiSettings s = nueva();

		s.activar(true, T0, QUIEN);
		assertThat(s.isEnabled()).isTrue();
		assertThat(s.getUpdatedBy()).isEqualTo(QUIEN);

		s.activar(false, T0, QUIEN);
		assertThat(s.isEnabled()).isFalse();
	}

	@Test
	@DisplayName("Cada funcion elige su propio proveedor")
	void cadaFuncionElSuyo() {
		// Validar requisitos son peticiones cortas y frecuentes; generar casos de uso,
		// pocas y largas. No tienen por que usar el mismo modelo.
		AiSettings pruebas = AiSettings.inicial(PROYECTO, AiFeature.GENERATE_TESTS, QUIEN, T0);
		AiSettings casos = AiSettings.inicial(PROYECTO, AiFeature.GENERATE_SPECS, QUIEN, T0);

		pruebas.configurar(AiProvider.DEEPSEEK, T0, QUIEN);
		casos.configurar(AiProvider.ANTHROPIC, T0, QUIEN);

		assertThat(pruebas.getProvider()).isEqualTo(AiProvider.DEEPSEEK);
		assertThat(casos.getProvider()).isEqualTo(AiProvider.ANTHROPIC);
		assertThat(pruebas.getFeature()).isNotEqualTo(casos.getFeature());
	}
}
