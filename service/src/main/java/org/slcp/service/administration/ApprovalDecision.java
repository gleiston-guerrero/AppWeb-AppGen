package org.slcp.service.administration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decision del administrador sobre una solicitud.
 *
 * @param approved si se aprueba
 * @param reason   motivo, obligatorio al rechazar conforme a ROL-05
 */
public record ApprovalDecision(
		@NotNull(message = "Indique si aprueba o rechaza la solicitud") Boolean approved,
		@Size(max = 500) String reason) {

	/** El rechazo sin motivo no es una decision util para quien la recibe. */
	public boolean rechazoSinMotivo() {
		return Boolean.FALSE.equals(approved) && (reason == null || reason.isBlank());
	}
}
