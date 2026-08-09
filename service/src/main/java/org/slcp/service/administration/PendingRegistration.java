package org.slcp.service.administration;

import java.time.Instant;

/** Solicitud de registro a la espera de decision. */
public record PendingRegistration(
		String readableId,
		String username,
		String email,
		String fullName,
		Instant requestedAt) {
}
