package org.slcp.service.recovery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slcp.service.recovery.RecoveryContracts.ChangePasswordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cambio de contrasena con sesion iniciada.
 *
 * <p>Cuelga de la sesion vigente y no de la coleccion de cuentas: se cambia la
 * contrasena de quien esta dentro, no la de una cuenta arbitraria.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/sessions/current")
public class PasswordController {

	private final PasswordRecoveryService service;

	public PasswordController(PasswordRecoveryService service) {
		this.service = service;
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiar(@Valid @RequestBody ChangePasswordRequest peticion,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {

		String origen = http.getHeader("X-Forwarded-For") != null
				? http.getHeader("X-Forwarded-For")
				: String.valueOf(http.getRemoteAddr());

		service.cambiar(UUID.fromString(jwt.getSubject()), peticion, origen);
	}
}
