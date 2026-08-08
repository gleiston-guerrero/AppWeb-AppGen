package org.slcp.service.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de la sesion.
 *
 * @param secret            clave de firma del token de acceso
 * @param accessTtl         vida del token de acceso, deliberadamente corta
 * @param refreshTtl        vida del token de renovacion
 * @param cookieSecure      exige transporte cifrado. Solo se desactiva en desarrollo local
 * @param accessCookieName  nombre de la cookie de acceso
 * @param refreshCookieName nombre de la cookie de renovacion
 */
@ConfigurationProperties(prefix = "slcp.session")
public record SessionProperties(
		String secret,
		Duration accessTtl,
		Duration refreshTtl,
		boolean cookieSecure,
		String accessCookieName,
		String refreshCookieName) {
}
