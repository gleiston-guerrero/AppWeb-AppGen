package org.slcp.service.domain;

/**
 * Funciones que pueden apoyarse en un modelo.
 *
 * <p>Cada una se configura por separado porque no piden lo mismo: validar
 * requisitos son peticiones cortas y frecuentes, donde interesa un modelo
 * barato; generar casos de uso son pocas peticiones largas, donde interesa el
 * mejor. Un unico proveedor para todo obliga a pagar el caro en lo que no lo
 * necesita, o a conformarse con el barato en lo que si.</p>
 *
 * <p>El campo {@code imprescindible} distingue las funciones que no pueden
 * hacerse sin modelo de las que tienen una via derivada. Donde la hay, la
 * plataforma sigue sirviendo sin configurar nada.</p>
 */
public enum AiFeature {

	VALIDATE_REQUIREMENTS("Validar requisitos",
			"Revisa enunciados y criterios mas alla de lo que las reglas pueden comprobar: si el "
					+ "criterio verifica de verdad lo que el enunciado exige.",
			false),

	GENERATE_TESTS("Generar pruebas",
			"Redacta los escenarios en Gherkin. Sin modelo se derivan del criterio de "
					+ "verificacion de cada requisito.",
			false),

	GENERATE_SPECS("Generar casos de uso e historias",
			"Redacta casos de uso expandidos e historias de usuario. No tiene via derivada: la "
					+ "accion del actor no esta en ningun requisito.",
			true),

	GENERATE_DIAGRAMS("Generar diagramas",
			"Identifica actores de dominio y relaciones que el texto no enuncia. Sin modelo se "
					+ "derivan de los enunciados, con los actores que puedan reconocerse.",
			false),

	GENERATE_CODE("Generar codigo",
			"Propone la implementacion que hace pasar las pruebas aceptadas. No tiene via "
					+ "derivada.",
			true);

	private final String etiqueta;
	private final String queHace;
	private final boolean imprescindible;

	AiFeature(String etiqueta, String queHace, boolean imprescindible) {
		this.etiqueta = etiqueta;
		this.queHace = queHace;
		this.imprescindible = imprescindible;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	public String getQueHace() {
		return queHace;
	}

	/** Si la funcion no puede realizarse sin un modelo configurado. */
	public boolean esImprescindible() {
		return imprescindible;
	}
}
