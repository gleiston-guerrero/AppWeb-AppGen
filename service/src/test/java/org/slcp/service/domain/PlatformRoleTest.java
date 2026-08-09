package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del rol global de plataforma. */
class PlatformRoleTest {

	@Test
	@DisplayName("La atribucion lleva el prefijo que espera la capa de autorizacion")
	void prefijo() {
		assertThat(PlatformRole.ADMINISTRATOR.authority()).isEqualTo("ROLE_ADMINISTRATOR");
		assertThat(PlatformRole.FACILITATOR.authority()).isEqualTo("ROLE_FACILITATOR");
		assertThat(PlatformRole.MEMBER.authority()).isEqualTo("ROLE_MEMBER");
	}

	@Test
	@DisplayName("El rol global solo expresa que puede hacerse sin proyecto")
	void tresAtribuciones() {
		assertThat(PlatformRole.values()).hasSize(3);
	}
}
