package org.slcp.service.projects;

/** La operacion no corresponde a quien la pide, o el proyecto no existe para el. */
public class ProjectAccessException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ProjectAccessException(String message) {
		super(message);
	}
}
