package org.slcp.service.invitations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.domain.ProjectRole;

/** Oraculo del mensaje de invitacion. */
class InvitationMessageTest {

	private static final String ENLACE = "http://localhost:4200/invitacion/abc123";

	@Test
	@DisplayName("El asunto nombra el proyecto")
	void asunto() {
		assertThat(InvitationMessage.asunto("MundiPets")).contains("MundiPets");
	}

	@Test
	@DisplayName("El cuerpo dice quien invita, a que y con que rol")
	void contenido() {
		String cuerpo = InvitationMessage.cuerpo("MundiPets", "Gleiston Guerrero",
				ProjectRole.TEAM_MEMBER, ENLACE, 7, false);

		assertThat(cuerpo).contains("Gleiston Guerrero");
		assertThat(cuerpo).contains("MundiPets");
		assertThat(cuerpo).contains("Miembro del equipo");
		assertThat(cuerpo).contains(ENLACE);
		assertThat(cuerpo).contains("7 dias");
	}

	@Test
	@DisplayName("INV-03: no revela nada del contenido del proyecto")
	void sinFiltraciones() {
		String cuerpo = InvitationMessage.cuerpo("MundiPets", "Gleiston",
				ProjectRole.PRODUCT_OWNER, ENLACE, 7, false).toLowerCase();

		assertThat(cuerpo).doesNotContain("requisito");
		assertThat(cuerpo).doesNotContain("equipo actual");
		assertThat(cuerpo).doesNotContain("contrasena:");
	}

	@Test
	@DisplayName("Explica que hacer segun se tenga cuenta o no")
	void instruccionesSegunElCaso() {
		String sinCuenta = InvitationMessage.cuerpo("P", "Q", ProjectRole.TEAM_MEMBER, ENLACE, 7, false);
		String conCuenta = InvitationMessage.cuerpo("P", "Q", ProjectRole.TEAM_MEMBER, ENLACE, 7, true);

		assertThat(sinCuenta).contains("completar su registro");
		assertThat(sinCuenta).contains("Enlace de la invitacion");
		assertThat(conCuenta).containsIgnoringCase("inicie sesion");
		assertThat(sinCuenta).isNotEqualTo(conCuenta);
	}

	@Test
	@DisplayName("Dice que no hacer nada si no se esperaba la invitacion")
	void avisoDeSeguridad() {
		String cuerpo = InvitationMessage.cuerpo("P", "Q", ProjectRole.TEAM_MEMBER, ENLACE, 7, false);

		assertThat(cuerpo).contains("no haga nada");
	}

	@Test
	@DisplayName("El enlace se compone sin barras duplicadas ni ausentes")
	void composicionDelEnlace() {
		assertThat(InvitationMessage.enlaceCompleto("http://localhost:4200/", "/invitacion/x"))
				.isEqualTo("http://localhost:4200/invitacion/x");
		assertThat(InvitationMessage.enlaceCompleto("http://localhost:4200", "invitacion/x"))
				.isEqualTo("http://localhost:4200/invitacion/x");
		assertThat(InvitationMessage.enlaceCompleto("https://slcp.uteq.edu.ec///", "/invitacion/x"))
				.isEqualTo("https://slcp.uteq.edu.ec/invitacion/x");
	}

	@Test
	@DisplayName("Cada rol trae su alcance explicado")
	void alcancePorRol() {
		for (ProjectRole rol : ProjectRole.values()) {
			assertThat(InvitationMessage.cuerpo("P", "Q", rol, ENLACE, 7, false))
					.contains(rol.getEtiqueta());
		}
	}
}
