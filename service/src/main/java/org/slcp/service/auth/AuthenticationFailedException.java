package org.slcp.service.auth;

/**
 * No se pudo abrir la sesion.
 *
 * <p>El motivo es explicito y el mensaje es preciso. Lo que impide enumerar
 * cuentas y probar contrasenas no es la vaguedad del mensaje, que solo estorba a
 * quien se equivoca de buena fe, sino la limitacion de intentos de
 * {@link LoginThrottle}.</p>
 */
public class AuthenticationFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final LoginFailure motivo;

	public AuthenticationFailedException(LoginFailure motivo) {
		super(motivo.getMensaje());
		this.motivo = motivo;
	}

	public LoginFailure getMotivo() {
		return motivo;
	}
}
