package org.slcp.service.planning;

/** La operacion sobre la descomposicion no puede realizarse tal como se pidio. */
public class PlanningException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PlanningException(String message) {
		super(message);
	}
}
