package org.slcp.service.requirements;

/** La operacion sobre requisitos no puede realizarse tal como se pidio. */
public class RequirementException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RequirementException(String message) {
		super(message);
	}
}
