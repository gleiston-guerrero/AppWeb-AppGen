package org.slcp.service.domain;

/**
 * Clase de recurso material.
 *
 * <p>Las personas no figuran aqui: ya son miembros del equipo, y tenerlas en dos
 * sitios acabaria con dos listas que discrepan.</p>
 */
public enum ResourceKind {

	EQUIPMENT("Equipo"),
	SOFTWARE("Programa o licencia"),
	FACILITY("Instalacion"),
	CONSUMABLE("Consumible"),
	SERVICE("Servicio contratado"),
	OTHER("Otro");

	private final String etiqueta;

	ResourceKind(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	public String getEtiqueta() {
		return etiqueta;
	}
}
