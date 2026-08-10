package org.slcp.service.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del correo de recuperacion. */
class RecoveryMessageTest {

	private static final String ENLACE = "http://localhost:4200/recuperar/abc123";

	@Test
	@DisplayName("El cuerpo trae el enlace y su plazo")
	void contenido() {
		String cuerpo = RecoveryMessage.cuerpo("Gleiston", ENLACE, 30);

		assertThat(cuerpo).contains(ENLACE);
		assertThat(cuerpo).contains("30 minutos");
		assertThat(cuerpo).contains("Gleiston");
	}

	@Test
	@DisplayName("Explica que hacer si no fue quien lo pidio")
	void avisoAnteSolicitudAjena() {
		String cuerpo = RecoveryMessage.cuerpo("Gleiston", ENLACE, 30);

		assertThat(cuerpo).contains("no ha sido usted");
		assertThat(cuerpo).contains("no haga nada");
	}

	@Test
	@DisplayName("Advierte de que se cerraran las sesiones abiertas")
	void avisoDeCierreDeSesiones() {
		assertThat(RecoveryMessage.cuerpo("Gleiston", ENLACE, 30)).contains("sesiones abiertas");
	}

	@Test
	@DisplayName("No revela la contrasena ni datos de la cuenta mas alla del nombre")
	void sinFiltraciones() {
		String cuerpo = RecoveryMessage.cuerpo("Gleiston", ENLACE, 30).toLowerCase();

		assertThat(cuerpo).doesNotContain("su contrasena es");
		assertThat(cuerpo).doesNotContain("usuario:");
	}
}
