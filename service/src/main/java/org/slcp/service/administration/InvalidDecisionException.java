package org.slcp.service.administration;

/** La decision no puede aplicarse tal como se planteo. */
public class InvalidDecisionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidDecisionException(String message) {
		super(message);
	}
}
