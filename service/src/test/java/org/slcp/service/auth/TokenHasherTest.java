package org.slcp.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del generador de tokens de renovacion. */
class TokenHasherTest {

	@Test
	@DisplayName("SEC-03: cada token generado es distinto")
	void tokensDistintos() {
		Set<String> vistos = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			vistos.add(TokenHasher.generar());
		}
		assertThat(vistos).hasSize(1000);
	}

	@Test
	@DisplayName("El token tiene entropia suficiente para no ser adivinable")
	void longitudSuficiente() {
		assertThat(TokenHasher.generar().length()).isGreaterThanOrEqualTo(43);
	}

	@Test
	@DisplayName("El resumen es estable: el mismo token produce siempre el mismo valor")
	void resumenEstable() {
		String token = TokenHasher.generar();
		assertThat(TokenHasher.resumir(token)).isEqualTo(TokenHasher.resumir(token));
	}

	@Test
	@DisplayName("SEC-03: el resumen no permite reconstruir el token")
	void resumenNoReversible() {
		String token = TokenHasher.generar();
		String resumen = TokenHasher.resumir(token);

		assertThat(resumen).doesNotContain(token).hasSize(64);
	}

	@Test
	@DisplayName("Dos tokens distintos producen resumenes distintos")
	void resumenesDistintos() {
		assertThat(TokenHasher.resumir(TokenHasher.generar()))
				.isNotEqualTo(TokenHasher.resumir(TokenHasher.generar()));
	}
}
