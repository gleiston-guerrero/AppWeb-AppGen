package org.slcp.service.registration;

/** El nombre de usuario o el correo ya estan en uso. */
public class RegistrationConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RegistrationConflictException(String message) {
		super(message);
	}
}
