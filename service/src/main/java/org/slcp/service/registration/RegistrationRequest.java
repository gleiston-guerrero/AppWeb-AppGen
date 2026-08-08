package org.slcp.service.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos que aporta quien solicita registrarse como facilitador de proyectos.
 *
 * <p>Realiza FUN-15. No incluye rol: el unico rol obtenible por autorregistro
 * es el de facilitador, y no se acepta que quien solicita lo indique, porque
 * seria una via de eleccion de privilegio.</p>
 */
public record RegistrationRequest(

		@NotBlank(message = "El nombre de usuario es obligatorio")
		@Size(min = 3, max = 60, message = "El nombre de usuario debe tener entre 3 y 60 caracteres")
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "El nombre de usuario solo admite letras, digitos, punto, guion y guion bajo")
		String username,

		@NotBlank(message = "El correo electronico es obligatorio")
		@Email(message = "El correo electronico no tiene un formato valido")
		@Size(max = 254)
		String email,

		@NotBlank(message = "El nombre completo es obligatorio")
		@Size(max = 160)
		String fullName,

		/*
		 * Longitud minima de quince caracteres y ninguna regla de composicion, de
		 * acuerdo con NIST SP 800-63B-4 y con FUN-05. Las reglas de composicion
		 * empujan a contrasenas predecibles; la longitud, no.
		 */
		@NotBlank(message = "La contrasena es obligatoria")
		@Size(min = 15, max = 200,
				message = "La contrasena debe tener al menos 15 caracteres. No se exige ninguna combinacion concreta de mayusculas, digitos ni simbolos")
		String password) {
}
