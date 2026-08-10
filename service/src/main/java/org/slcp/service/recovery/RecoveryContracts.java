package org.slcp.service.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos de la recuperacion de acceso. */
public final class RecoveryContracts {

	private RecoveryContracts() {
	}

	/** Peticion de recuperacion. Admite usuario o correo, como el acceso (FUN-03). */
	public record ResetRequest(
			@NotBlank(message = "Indique su nombre de usuario o su correo") String identifier) {
	}

	/** Nueva contrasena, presentada con el enlace. */
	public record NewPasswordRequest(
			@NotBlank(message = "La contrasena es obligatoria")
			@Size(min = 15, max = 200,
					message = "La contrasena debe tener al menos 15 caracteres")
			String password) {
	}

	/** Cambio de contrasena con sesion iniciada. */
	public record ChangePasswordRequest(
			@NotBlank(message = "Indique su contrasena actual") String currentPassword,

			@NotBlank(message = "La contrasena nueva es obligatoria")
			@Size(min = 15, max = 200,
					message = "La contrasena debe tener al menos 15 caracteres")
			String newPassword) {
	}

	/**
	 * Respuesta a la peticion de recuperacion.
	 *
	 * <p>No contiene enlace ni token. Quien pide una recuperacion es anonimo, de
	 * modo que devolverle el enlace entregaria cualquier cuenta a quien la
	 * pidiese. Esta es la diferencia con las invitaciones, donde quien invita
	 * esta autenticado y autorizado.</p>
	 */
	public record ResetResponse(boolean sent, String message) {
	}

	/** Estado de un enlace, antes de usarlo. */
	public record ResetPreview(boolean valid, String reason, String username) {
	}
}
