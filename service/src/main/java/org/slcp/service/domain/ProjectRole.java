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
	 * ROL-06: en un mismo proyecto, quien aprueba no hace nada mas.
	 *
	 * <p>El propietario del producto es incompatible con cualquier otro rol del
	 * proyecto. Con el miembro del equipo porque quien produce no puede aprobar lo
	 * que produce; con el facilitador porque este da la revision previa de RQM-05,
	 * y acumular ambos dejaria las dos etapas de aprobacion en manos de una sola
	 * persona: una firma con dos nombres.</p>
	 *
	 * <p>Facilitador y miembro del equipo si son compatibles: organizar y ejecutar
	 * no se vigilan mutuamente, y en un equipo pequeno separarlos seria un estorbo
	 * sin contrapartida.</p>
	 *
	 * <p>La regla se comprueba aqui para poder explicarla, y la impone ademas la
	 * base de datos para que ninguna via de acceso la sortee.</p>
	 */
	public boolean incompatibleCon(ProjectRole otro) {
		return (this == PRODUCT_OWNER) != (otro == PRODUCT_OWNER);
	}

	/** Explicacion de por que dos roles no pueden coincidir. */
	public String motivoDeIncompatibilidad(ProjectRole otro) {
		if (!incompatibleCon(otro)) {
			return "";
		}
		ProjectRole elOtro = this == PRODUCT_OWNER ? otro : this;

		return elOtro == TEAM_MEMBER
				? "ROL-06: quien produce no puede aprobar en el mismo proyecto"
				: "ROL-06: el facilitador da la revision previa y el propietario la aprobacion "
						+ "definitiva. Acumular ambos roles dejaria las dos etapas en una sola persona";
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
