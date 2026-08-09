package org.slcp.service.administration;

/** No existe solicitud con ese identificador. */
public class RegistrationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RegistrationNotFoundException(String readableId) {
		super("No existe ninguna solicitud con el identificador " + readableId);
	}
}
