package org.slcp.service.invitations;

/** La invitacion no puede emitirse, usarse o resolverse tal como se pidio. */
public class InvitationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvitationException(String message) {
		super(message);
	}
}
