package org.slcp.service.invitations;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del envio de correo.
 *
 * @param from     direccion remitente. Vacia desactiva el envio
 * @param fromName nombre visible del remitente
 * @param baseUrl  direccion publica desde la que se construyen los enlaces
 */
@ConfigurationProperties(prefix = "slcp.mail")
public record MailProperties(String from, String fromName, String baseUrl) {

	/** El envio se activa por la presencia de remitente, no por una marca aparte. */
	public boolean activo() {
		return from != null && !from.isBlank();
	}
}
