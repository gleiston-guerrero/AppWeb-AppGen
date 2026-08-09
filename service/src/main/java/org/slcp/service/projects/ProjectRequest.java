package org.slcp.service.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear un proyecto. */
public record ProjectRequest(
		@NotBlank(message = "El nombre del proyecto es obligatorio")
		@Size(max = 160) String name,

		@Size(max = 1000) String purpose) {
}
