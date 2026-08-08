package org.slcp.service.auth;

/**
 * Motivo por el que no se pudo abrir sesion.
 *
 * <p>Cada motivo lleva el mensaje exacto que se devuelve. La precision es
 * deliberada: un mensaje vago no protege nada que el formulario de registro no
 * revele ya, y en cambio impide a la persona corregir su error.</p>
 *
 * <p>Lo que si se cuida es no revelar mas de lo necesario sobre el estado de una
 * cuenta ajena: se dice que la cuenta no esta operativa y por que, sin detallar
 * quien la rechazo ni cuando.</p>
 */
public enum LoginFailure {

	/** No existe cuenta alguna con ese nombre de usuario ni con ese correo. */
	UNKNOWN_IDENTIFIER("No existe ninguna cuenta con ese nombre de usuario ni con ese correo"),

	/** La cuenta existe y la contrasena no coincide. */
	BAD_PASSWORD("La contrasena no es correcta"),

	/** La cuenta existe y aun no la ha aprobado el administrador. */
	PENDING_APPROVAL("Su solicitud de registro aun no ha sido aprobada por el administrador de la plataforma"),

	/** La solicitud fue rechazada. */
	REJECTED("Su solicitud de registro fue rechazada. Consulte con el administrador de la plataforma"),

	/** La cuenta fue retirada del servicio. */
	DECOMMISSIONED("Su cuenta fue dada de baja. Consulte con el administrador de la plataforma"),

	/** La invitacion no se completo: la cuenta existe pero no tiene contrasena. */
	REGISTRATION_INCOMPLETE("Su registro no esta completo. Use el enlace de invitacion que recibio por correo"),

	/** Demasiados intentos fallidos seguidos. */
	TOO_MANY_ATTEMPTS("Demasiados intentos fallidos. Vuelva a intentarlo dentro de unos minutos");

	private final String mensaje;

	LoginFailure(String mensaje) {
		this.mensaje = mensaje;
	}

	public String getMensaje() {
		return mensaje;
	}
}
