package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de los roles de proyecto y su segregacion. */
class ProjectRoleTest {

	@Test
	@DisplayName("ROL-06: quien produce no puede aprobar en el mismo proyecto")
	void segregacion() {
		assertThat(ProjectRole.TEAM_MEMBER.incompatibleCon(ProjectRole.PRODUCT_OWNER)).isTrue();
		assertThat(ProjectRole.PRODUCT_OWNER.incompatibleCon(ProjectRole.TEAM_MEMBER)).isTrue();
	}

	@Test
	@DisplayName("El facilitador si puede sumar otro rol: organiza, no aprueba")
	void facilitadorCompatible() {
		assertThat(ProjectRole.PROJECT_FACILITATOR.incompatibleCon(ProjectRole.TEAM_MEMBER)).isFalse();
		assertThat(ProjectRole.PROJECT_FACILITATOR.incompatibleCon(ProjectRole.PRODUCT_OWNER)).isFalse();
	}

	@Test
	@DisplayName("ROL-02: solo el miembro del equipo modifica")
	void soloElEquipoModifica() {
		assertThat(ProjectRole.TEAM_MEMBER.puedeModificar()).isTrue();
		assertThat(ProjectRole.PRODUCT_OWNER.puedeModificar()).isFalse();
		assertThat(ProjectRole.PROJECT_FACILITATOR.puedeModificar()).isFalse();
	}

	@Test
	@DisplayName("ROL-03: solo el propietario aprueba")
	void soloElPropietarioAprueba() {
		assertThat(ProjectRole.PRODUCT_OWNER.puedeAprobar()).isTrue();
		assertThat(ProjectRole.TEAM_MEMBER.puedeAprobar()).isFalse();
		assertThat(ProjectRole.PROJECT_FACILITATOR.puedeAprobar()).isFalse();
	}

	@Test
	@DisplayName("Nadie modifica y aprueba a la vez")
	void nadieAmbas() {
		for (ProjectRole rol : ProjectRole.values()) {
			assertThat(rol.puedeModificar() && rol.puedeAprobar()).isFalse();
		}
	}

	@Test
	@DisplayName("Cada rol tiene etiqueta propia para la interfaz")
	void etiquetas() {
		for (ProjectRole rol : ProjectRole.values()) {
			assertThat(rol.getEtiqueta()).isNotBlank();
		}
	}
}
