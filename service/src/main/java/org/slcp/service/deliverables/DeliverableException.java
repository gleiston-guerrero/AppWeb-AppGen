package org.slcp.service.deliverables;

/** La operacion sobre entregables no puede realizarse tal como se pidio. */
public class DeliverableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DeliverableException(String message) {
		super(message);
	}
}
