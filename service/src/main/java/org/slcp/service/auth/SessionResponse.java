package org.slcp.service.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Datos de la sesion iniciada.
 *
 * <p>No incluye token alguno: los tokens viajan en cookies que el codigo de la
 * pagina no puede leer, conforme a SEC-01. Lo que aqui se devuelve es lo que la
 * interfaz necesita para saber a quien esta atendiendo.</p>
 */
public record SessionResponse(
		UUID userId,
		String readableId,
		String username,
		String fullName,
		/** Rol global. La interfaz lo usa para decidir que mostrar, nunca para autorizar. */
		String platformRole,
		/** Obliga a la interfaz a llevar a la persona a cambiar su contrasena. */
		boolean mustChangePassword,
		Instant expiresAt) {
}
