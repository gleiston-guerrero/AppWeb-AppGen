package org.slcp.service.domain;

/**
 * Rol dentro de un proyecto.
 *
 * <p>Se resuelve siempre respecto del proyecto sobre el que se actua, nunca
 * respecto de la cuenta (ROL-01). Una misma persona puede ser desarrolladora en
 * un proyecto y propietaria en otro.</p>
 */
public enum ProjectRole {

	/** Organiza: crea el proyecto, planifica y designa al equipo. */
	PROJECT_FACILITATOR("Facilitador de proyectos"),

	/** Ejecuta: requisitos, generacion, modificacion. */
	TEAM_MEMBER("Miembro del equipo"),

	/** Verifica y aprueba. No modifica nada. */
	PRODUCT_OWNER("Propietario del producto");

	private final String etiqueta;

	ProjectRole(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	/**
	 * ROL-06: quien produce no puede aprobar en el mismo proyecto.
	 *
	 * <p>La regla se comprueba aqui para poder explicarla, y la impone ademas la
	 * base de datos para que ninguna via de acceso la sortee.</p>
	 */
	public boolean incompatibleCon(ProjectRole otro) {
		return (this == TEAM_MEMBER && otro == PRODUCT_OWNER)
				|| (this == PRODUCT_OWNER && otro == TEAM_MEMBER);
	}

	/** Indica si el rol permite modificar artefactos del proyecto. */
	public boolean puedeModificar() {
		return this == TEAM_MEMBER;
	}

	/** Indica si el rol aprueba o reprueba lo producido (ROL-03). */
	public boolean puedeAprobar() {
		return this == PRODUCT_OWNER;
	}
}
