package org.slcp.core.naming;

/**
 * Clase de identificador que se desea obtener.
 *
 * <p>La distincion importa porque el estilo depende del par destino-clase y
 * porque solo TABLE y PATH emplean la forma plural, que segun NAM-07 procede
 * siempre del glosario y nunca de una inferencia.</p>
 */
public enum Kind {

	/** Tipo: clase, interfaz, entidad. */
	TYPE,
	/** Miembro: atributo, metodo, propiedad, parametro, clave de serializacion. */
	MEMBER,
	/** Constante o literal de enumeracion. */
	CONSTANT,
	/** Tabla de base de datos; usa la forma plural. */
	TABLE,
	/** Columna de base de datos; usa la forma singular. */
	COLUMN,
	/** Segmento de ruta REST; usa la forma plural. */
	PATH,
	/** Nombre de archivo. */
	FILE
}
