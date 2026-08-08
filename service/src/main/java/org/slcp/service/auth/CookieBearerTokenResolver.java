package org.slcp.service.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Localiza el token de acceso en la cookie en lugar de en la cabecera.
 *
 * <p>Realiza SEC-01: el token no lo entrega el codigo de la pagina, porque no
 * puede leerlo. Lo envia el navegador por si mismo, y por eso hace falta un
 * localizador propio en lugar del que espera una cabecera de portador.</p>
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

	private final String nombreCookie;

	public CookieBearerTokenResolver(String nombreCookie) {
		this.nombreCookie = nombreCookie;
	}

	@Override
	public String resolve(HttpServletRequest peticion) {
		Cookie[] cookies = peticion.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (nombreCookie.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
