package org.slcp.service.auth;

/**
 * Credenciales invalidas o cuenta no operativa.
 *
 * <p>El mensaje es deliberadamente el mismo en ambos casos: distinguirlos
 * revelaria a quien lo intenta si una cuenta existe, y con ello convertiria el
 * formulario de acceso en un instrumento para enumerar personas registradas.</p>
 */
public class AuthenticationFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AuthenticationFailedException() {
		super("Credenciales no validas");
	}
}
