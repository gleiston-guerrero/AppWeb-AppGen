package org.slcp.service.invitations;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.slcp.service.domain.ProjectRole;

/** Contratos de las invitaciones, agrupados por pertenecer al mismo flujo. */
public final class InvitationContracts {

	private InvitationContracts() {
	}

	/**
	 * Peticion de invitacion.
	 *
	 * <p>El rol lo fija quien invita. Nunca viaja en el formulario de quien
	 * completa el registro, conforme a INV-02.</p>
	 */
	public record InviteRequest(
			@NotBlank(message = "Indique el correo de quien se incorpora")
			@Email(message = "El correo no tiene un formato valido")
			@Size(max = 254) String email,

			@NotNull(message = "Indique el rol") ProjectRole role) {
	}

	/** Camino que sigue una incorporacion, segun a quien se incorpora. */
	public enum Camino {
		/** La persona ya tenia cuenta y queda incorporada de inmediato. */
		INCORPORADA_DIRECTAMENTE,

		/** La persona tenia cuenta: se le propone y debe aceptar. */
		PENDIENTE_DE_ACEPTACION,

		/** La persona no tenia cuenta: debe completar su registro. */
		PENDIENTE_DE_REGISTRO
	}

	/**
	 * Resultado de invitar.
	 *
	 * @param link enlace de un solo uso. Solo se devuelve mientras la plataforma
	 *             no envie correo por si misma; ver la advertencia de
	 *             {@link InvitationService}
	 */
	public record InviteResult(
			Camino camino,
			String email,
			String role,
			String roleLabel,
			Instant expiresAt,
			String link,
			String message) {
	}

	/** Invitacion vigente, tal como la ve el facilitador. */
	public record PendingInvite(
			String id,
			String email,
			String role,
			String roleLabel,
			Instant createdAt,
			Instant expiresAt,
			boolean tieneCuenta) {
	}

	/** Lo que ve quien abre un enlace de invitacion, antes de decidir. */
	public record InvitationPreview(
			boolean valid,
			String reason,
			String projectName,
			String projectReadableId,
			String invitedBy,
			String email,
			String role,
			String roleLabel,
			String roleScope,
			boolean requiereRegistro) {
	}

	/**
	 * Datos para completar el registro desde una invitacion.
	 *
	 * <p>No incluye correo ni rol: el correo viene ligado al enlace y el rol lo
	 * fijo quien invito. Aceptarlos aqui seria abrir una via de eleccion de
	 * privilegio y de verificacion falsa del correo.</p>
	 */
	public record CompletionRequest(
			@NotBlank(message = "El nombre de usuario es obligatorio")
			@Size(min = 3, max = 60, message = "Entre 3 y 60 caracteres")
			@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
					message = "Solo letras, digitos, punto, guion y guion bajo")
			String username,

			@NotBlank(message = "El nombre completo es obligatorio")
			@Size(max = 160) String fullName,

			@NotBlank(message = "La contrasena es obligatoria")
			@Size(min = 15, max = 200,
					message = "La contrasena debe tener al menos 15 caracteres")
			String password) {
	}

	/** Resultado de completar o aceptar. */
	public record JoinResult(
			String projectReadableId,
			String projectName,
			String role,
			String roleLabel,
			String message) {
	}
}
