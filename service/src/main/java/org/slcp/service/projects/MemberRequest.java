package org.slcp.service.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slcp.service.domain.ProjectRole;

/**
 * Incorporacion de una persona al equipo.
 *
 * @param identifier nombre de usuario o correo de quien se incorpora
 * @param role       rol que le asigna el facilitador. Nunca lo elige quien entra
 */
public record MemberRequest(
		@NotBlank(message = "Indique el nombre de usuario o el correo") String identifier,
		@NotNull(message = "Indique el rol") ProjectRole role) {
}
