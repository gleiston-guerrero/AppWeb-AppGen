package org.slcp.service.registration;

import java.time.Instant;

/**
 * Respuesta a una solicitud de registro.
 *
 * <p>Devuelve el identificador legible y el estado, para que quien solicita
 * sepa que su cuenta queda pendiente de aprobacion y no operativa.</p>
 */
public record RegistrationResponse(
		String readableId,
		String username,
		String status,
		Instant requestedAt,
		String message) {
}
