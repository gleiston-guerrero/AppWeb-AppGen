package org.slcp.service.administration;

import java.time.Instant;

/** Resultado de la decision, con el estado en que queda la cuenta. */
public record ApprovalResult(
		String readableId,
		String username,
		String status,
		Instant decidedAt,
		String message) {
}
