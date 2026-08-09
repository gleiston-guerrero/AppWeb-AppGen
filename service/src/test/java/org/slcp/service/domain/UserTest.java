package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de la cuenta de usuario. */
class UserTest {

	private static final Instant MOMENTO = Instant.parse("2026-08-07T00:00:00Z");
	private static final String VERIFICADOR = "$2a$12$verificadorDePruebaNoEsUnaContrasenaReal000000000000";

	@Test
	@DisplayName("FUN-15: la solicitud nace pendiente de aprobacion")
	void nacePendiente() {
		User usuario = User.solicitar("gguerrero", "G.Guerrero@uteq.edu.ec", "Gleiston Guerrero", VERIFICADOR, 1, MOMENTO);

		assertThat(usuario.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
	}

	@Test
	@DisplayName("TRC-03: el identificador legible sigue el esquema acordado")
	void identificadorLegible() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 7, MOMENTO);

		assertThat(usuario.getReadableId()).isEqualTo("USR-ACC-0007-v1");
		assertThat(usuario.getId()).isNotNull();
	}

	@Test
	@DisplayName("El correo se normaliza a minusculas para que la busqueda no sea ambigua")
	void correoNormalizado() {
		User usuario = User.solicitar("  gguerrero  ", "  G.Guerrero@UTEQ.edu.ec ", " Gleiston ", VERIFICADOR, 1, MOMENTO);

		assertThat(usuario.getEmail()).isEqualTo("g.guerrero@uteq.edu.ec");
		assertThat(usuario.getUsername()).isEqualTo("gguerrero");
		assertThat(usuario.getFullName()).isEqualTo("Gleiston");
	}

	@Test
	@DisplayName("FUN-04: la cuenta guarda el verificador, nunca la contrasena")
	void guardaVerificador() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		assertThat(usuario.getPasswordVerifier()).isEqualTo(VERIFICADOR);
	}

	@Test
	@DisplayName("FUN-15: una cuenta pendiente no puede iniciar sesion")
	void pendienteNoAccede() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		assertThat(usuario.puedeIniciarSesion()).isFalse();

		usuario.transitarA(UserStatus.ACTIVE);
		assertThat(usuario.puedeIniciarSesion()).isTrue();
	}

	@Test
	@DisplayName("FUN-16: aprobar deja la cuenta operativa")
	void aprobar() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		usuario.aprobar();

		assertThat(usuario.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(usuario.puedeIniciarSesion()).isTrue();
	}

	@Test
	@DisplayName("FUN-16: el rechazo es terminal y no admite rectificacion")
	void rechazoTerminal() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		usuario.rechazar();

		assertThat(usuario.getStatus()).isEqualTo(UserStatus.REJECTED);
		assertThatThrownBy(usuario::aprobar).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("Una cuenta nace sin atribuciones de plataforma")
	void sinAtribuciones() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		assertThat(usuario.getPlatformRole()).isEqualTo(PlatformRole.MEMBER);
		assertThat(usuario.esAdministrador()).isFalse();
	}

	@Test
	@DisplayName("FUN-15: el autorregistro concede la atribucion de facilitador")
	void autorregistroConcedeFacilitador() {
		User usuario = User.solicitarComoFacilitador("gguerrero", "g@uteq.edu.ec", "Gleiston",
				VERIFICADOR, 1, MOMENTO);

		assertThat(usuario.getPlatformRole()).isEqualTo(PlatformRole.FACILITATOR);
		assertThat(usuario.esAdministrador()).isFalse();
		// La atribucion existe, pero no surte efecto hasta que la cuenta es operativa
		assertThat(usuario.puedeIniciarSesion()).isFalse();
	}

	@Test
	@DisplayName("Cambiar el verificador levanta la obligacion de cambiarlo")
	void cambioDeVerificador() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		usuario.cambiarVerificador("$2a$12$otroVerificadorDistintoDelAnterior0000000000000000000");

		assertThat(usuario.getPasswordVerifier()).isNotEqualTo(VERIFICADOR);
		assertThat(usuario.isMustChangePassword()).isFalse();
	}

	@Test
	@DisplayName("Una transicion no admitida se rechaza en lugar de aplicarse")
	void transicionNoAdmitida() {
		User usuario = User.solicitar("gguerrero", "g@uteq.edu.ec", "Gleiston", VERIFICADOR, 1, MOMENTO);

		assertThatThrownBy(() -> usuario.transitarA(UserStatus.DECOMMISSIONED))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no admitida");

		assertThat(usuario.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
	}
}
