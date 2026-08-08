package org.slcp.service.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de acceso.
 *
 * @param identifier nombre de usuario o correo, indistintamente (FUN-03)
 * @param password   contrasena en claro, que no se registra ni se conserva
 */
public record LoginRequest(
		@NotBlank(message = "Indique su nombre de usuario o su correo") String identifier,
		@NotBlank(message = "Indique su contrasena") String password) {
}
