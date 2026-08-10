package org.slcp.service.domain;

/** Naturaleza de un requisito. Determina que campos se le exigen. */
public enum RequirementKind {

	FUNCTIONAL("Requisito funcional"),
	NON_FUNCTIONAL("Requisito no funcional"),
	CONSTRAINT("Restriccion o requisito de diseno"),
	USER_STORY("Historia de usuario"),
	USE_CASE("Caso de uso"),
	OTHER("Otro");

	private final String etiqueta;

	RequirementKind(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	/**
	 * Deduce la naturaleza a partir del identificador de origen.
	 *
	 * <p>Es una conjetura y se aplica solo al importar, donde no hay quien lo
	 * declare. Quien revisa puede corregirla: TRC-13 distingue lo que el
	 * documento dice de lo que se supone que quiso decir.</p>
	 */
	public static RequirementKind conjeturar(String sourceId) {
		if (sourceId == null) {
			return OTHER;
		}
		String s = sourceId.trim().toUpperCase();
		if (s.startsWith("RNF")) {
			return NON_FUNCTIONAL;
		}
		if (s.startsWith("RF")) {
			return FUNCTIONAL;
		}
		if (s.startsWith("RD") || s.startsWith("RES") || s.startsWith("CON")) {
			return CONSTRAINT;
		}
		if (s.startsWith("HU") || s.startsWith("US")) {
			return USER_STORY;
		}
		if (s.startsWith("CU") || s.startsWith("UC")) {
			return USE_CASE;
		}
		return OTHER;
	}

	/** Indica si a este tipo se le exige criterio de verificacion propio. */
	public boolean exigeCriterio() {
		return this != USE_CASE;
	}
}
