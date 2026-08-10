package org.slcp.service.recovery;

/** La recuperacion no puede realizarse tal como se pidio. */
public class RecoveryException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecoveryException(String message) {
		super(message);
	}
}
