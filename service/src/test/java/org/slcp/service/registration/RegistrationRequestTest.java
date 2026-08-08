package org.slcp.service.registration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo de la validacion de la solicitud.
 *
 * <p>Se ejecuta sin contexto de Spring: el validador se construye de forma
 * directa, de modo que la prueba mide la regla y no la infraestructura.</p>
 */
class RegistrationRequestTest {

	private static Validator validador;
	private static final String CONTRASENA = "una frase larga de acceso";

	@BeforeAll
	static void iniciar() {
		try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
			validador = fabrica.getValidator();
		}
	}

	@Test
	@DisplayName("Una solicitud completa y bien formada no produce infracciones")
	void solicitudValida() {
		var peticion = new RegistrationRequest("gguerrero", "gguerrero@uteq.edu.ec", "Gleiston Guerrero", CONTRASENA);

		assertThat(validador.validate(peticion)).isEmpty();
	}

	@Test
	@DisplayName("El correo mal formado se rechaza")
	void correoInvalido() {
		var peticion = new RegistrationRequest("gguerrero", "esto-no-es-un-correo", "Gleiston", CONTRASENA);

		assertThat(validador.validate(peticion)).hasSize(1);
	}

	@Test
	@DisplayName("El nombre de usuario con espacios se rechaza, por producir ambiguedad al iniciar sesion")
	void usuarioConEspacios() {
		var peticion = new RegistrationRequest("gleiston guerrero", "g@uteq.edu.ec", "Gleiston", CONTRASENA);

		assertThat(validador.validate(peticion)).hasSize(1);
	}

	@Test
	@DisplayName("FUN-05: se rechaza la contrasena corta")
	void contrasenaCorta() {
		var peticion = new RegistrationRequest("gguerrero", "g@uteq.edu.ec", "Gleiston", "corta123");

		assertThat(validador.validate(peticion)).hasSize(1);
	}

	@Test
	@DisplayName("FUN-05: no se exige ninguna combinacion concreta de caracteres")
	void sinReglasDeComposicion() {
		var peticion = new RegistrationRequest("gguerrero", "g@uteq.edu.ec", "Gleiston",
				"todo en minusculas y sin digitos ni simbolos");

		assertThat(validador.validate(peticion)).isEmpty();
	}

	@Test
	@DisplayName("Los campos obligatorios vacios se rechazan todos a la vez")
	void camposVacios() {
		var peticion = new RegistrationRequest("", "", "", "");

		assertThat(validador.validate(peticion)).hasSizeGreaterThanOrEqualTo(4);
	}
}
