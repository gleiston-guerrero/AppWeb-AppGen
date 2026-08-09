package org.slcp.service.domain;

/**
 * Rol global de plataforma.
 *
 * <p>Es el unico rol que no se resuelve por proyecto. ROL-01 establece que los
 * demas dependen del proyecto sobre el que se actua; la administracion de la
 * plataforma no pertenece a ninguno.</p>
 */
public enum PlatformRole {

	/** Sin atribuciones de plataforma. Participa donde se le incorpore. */
	MEMBER,

	/** Puede crear proyectos. Se obtiene por autorregistro aprobado (FUN-15). */
	FACILITATOR,

	/** Aprueba registros de facilitadores y da de baja proyectos y cuentas. */
	ADMINISTRATOR;

	/** Nombre de la atribucion tal como la espera la capa de autorizacion. */
	public String authority() {
		return "ROLE_" + name();
	}
}
