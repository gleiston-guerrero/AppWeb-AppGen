package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de las credenciales.
 *
 * <p>Una por proveedor, y varias conviven: es lo que permite comparar cuatro
 * APIs en un ensayo sin perder las demas al cambiar de una.</p>
 */
class AiCredentialTest {

	private static final Instant T0 = Instant.parse("2026-08-13T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	private AiCredential nueva(AiProvider proveedor) {
		return AiCredential.crear(PROYECTO, proveedor, null, null, "cifrada", "…2b7a", QUIEN, T0);
	}

	@Test
	@DisplayName("Sin modelo ni direccion se toman los habituales del proveedor")
	void valoresPorDefecto() {
		AiCredential c = nueva(AiProvider.OPENAI);

		assertThat(c.getModel()).isEqualTo(AiProvider.OPENAI.getModeloPorDefecto());
		assertThat(c.getBaseUrl()).isEqualTo(AiProvider.OPENAI.getDireccionPorDefecto());
	}

	@Test
	@DisplayName("Cambiar el modelo no toca la clave")
	void cambiarModeloConservaClave() {
		// Corregir el nombre del modelo no debe obligar a teclear la clave otra vez,
		// y quien no la tuviera a mano la borraria sin querer.
		AiCredential c = nueva(AiProvider.ANTHROPIC);
		c.actualizar("otro-modelo", null, null, null, QUIEN, T0);

		assertThat(c.getModel()).isEqualTo("otro-modelo");
		assertThat(c.getApiKeyCipher()).isEqualTo("cifrada");
		assertThat(c.getKeyHint()).isEqualTo("…2b7a");
	}

	@Test
	@DisplayName("Al llegar una clave nueva se sustituyen clave y pista")
	void claveNuevaSustituye() {
		AiCredential c = nueva(AiProvider.ANTHROPIC);
		c.actualizar(null, null, "otraCifrada", "…9999", QUIEN, T0);

		assertThat(c.getApiKeyCipher()).isEqualTo("otraCifrada");
		assertThat(c.getKeyHint()).isEqualTo("…9999");
	}

	@Test
	@DisplayName("Credenciales de proveedores distintos son independientes")
	void independientes() {
		// Guardar la de OpenAI no borra la de DeepSeek: sin esto, comparar cuatro
		// APIs seria imposible.
		AiCredential deepseek = nueva(AiProvider.DEEPSEEK);
		AiCredential openai = AiCredential.crear(PROYECTO, AiProvider.OPENAI, null, null,
				"otra", "…8888", QUIEN, T0);

		assertThat(deepseek.getProvider()).isNotEqualTo(openai.getProvider());
		assertThat(deepseek.getApiKeyCipher()).isNotEqualTo(openai.getApiKeyCipher());
	}
}
