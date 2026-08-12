package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de los roles de proyecto y su segregacion. */
class ProjectRoleTest {

	@Test
	@DisplayName("ROL-06: quien produce no puede aprobar en el mismo proyecto")
	void produccionNoAprueba() {
		assertThat(ProjectRole.TEAM_MEMBER.incompatibleCon(ProjectRole.PRODUCT_OWNER)).isTrue();
		assertThat(ProjectRole.PRODUCT_OWNER.incompatibleCon(ProjectRole.TEAM_MEMBER)).isTrue();
	}

	@Test
	@DisplayName("ROL-06: el facilitador tampoco puede ser propietario en el mismo proyecto")
	void facilitadorNoAprueba() {
		// El facilitador da la revision previa de RQM-05 y el propietario la
		// aprobacion definitiva. Acumular ambos dejaria las dos etapas en una sola
		// persona, que es una firma con dos nombres.
		assertThat(ProjectRole.PROJECT_FACILITATOR.incompatibleCon(ProjectRole.PRODUCT_OWNER)).isTrue();
		assertThat(ProjectRole.PRODUCT_OWNER.incompatibleCon(ProjectRole.PROJECT_FACILITATOR)).isTrue();
	}

	@Test
	@DisplayName("Facilitador y miembro del equipo si son compatibles")
	void organizarYEjecutarCompatibles() {
		// Organizar y ejecutar no se vigilan mutuamente, y en un equipo pequeno
		// separarlos seria un estorbo sin contrapartida.
		assertThat(ProjectRole.PROJECT_FACILITATOR.incompatibleCon(ProjectRole.TEAM_MEMBER)).isFalse();
		assertThat(ProjectRole.TEAM_MEMBER.incompatibleCon(ProjectRole.PROJECT_FACILITATOR)).isFalse();
	}

	@Test
	@DisplayName("Un rol nunca es incompatible consigo mismo")
	void compatibleConsigoMismo() {
		for (ProjectRole rol : ProjectRole.values()) {
			assertThat(rol.incompatibleCon(rol)).isFalse();
		}
	}

	@Test
	@DisplayName("El propietario es el unico rol exclusivo")
	void propietarioExclusivo() {
		for (ProjectRole otro : ProjectRole.values()) {
			boolean incompatible = ProjectRole.PRODUCT_OWNER.incompatibleCon(otro);
			assertThat(incompatible).isEqualTo(otro != ProjectRole.PRODUCT_OWNER);
		}
	}

	@Test
	@DisplayName("Cada incompatibilidad explica su motivo, y solo cuando la hay")
	void motivosDeIncompatibilidad() {
		assertThat(ProjectRole.PRODUCT_OWNER.motivoDeIncompatibilidad(ProjectRole.TEAM_MEMBER))
				.contains("quien produce no puede aprobar");
		assertThat(ProjectRole.PRODUCT_OWNER.motivoDeIncompatibilidad(ProjectRole.PROJECT_FACILITATOR))
				.contains("dos etapas");
		assertThat(ProjectRole.PROJECT_FACILITATOR.motivoDeIncompatibilidad(ProjectRole.TEAM_MEMBER))
				.isEmpty();
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
