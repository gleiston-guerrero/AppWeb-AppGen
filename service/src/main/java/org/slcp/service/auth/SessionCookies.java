package org.slcp.service.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Construccion de las cookies de sesion.
 *
 * <p>Realiza SEC-01. Las tres marcas importan y cada una ataja algo distinto:
 * {@code HttpOnly} impide que el codigo de la pagina lea el token, {@code Secure}
 * impide que viaje sin cifrar, y {@code SameSite=Strict} impide que el navegador
 * lo envie en peticiones originadas por otro sitio.</p>
 */
public final class SessionCookies {

	private SessionCookies() {
	}

	public static ResponseCookie acceso(String nombre, String valor, Duration vida, boolean segura) {
		return base(nombre, valor, vida, segura).path("/api").build();
	}

	/**
	 * La cookie de renovacion se restringe a su propia ruta: asi no acompana a
	 * cada peticion ordinaria y su exposicion es mucho menor.
	 */
	public static ResponseCookie renovacion(String nombre, String valor, Duration vida, boolean segura) {
		return base(nombre, valor, vida, segura).path("/api/v1/auth").build();
	}

	public static ResponseCookie borrar(String nombre, String ruta, boolean segura) {
		return ResponseCookie.from(nombre, "")
				.httpOnly(true).secure(segura).sameSite("Strict")
				.path(ruta).maxAge(0).build();
	}

	private static ResponseCookie.ResponseCookieBuilder base(String nombre, String valor,
			Duration vida, boolean segura) {
		return ResponseCookie.from(nombre, valor)
				.httpOnly(true)
				.secure(segura)
				.sameSite("Strict")
				.maxAge(vida);
	}
}
