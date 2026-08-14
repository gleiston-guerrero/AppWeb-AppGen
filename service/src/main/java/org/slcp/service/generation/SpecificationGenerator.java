package org.slcp.service.generation;

import java.util.List;

/**
 * Genera casos de uso expandidos e historias de usuario.
 *
 * <p>Los campos son los de las tablas 8 y 9 del manuscrito, derivadas de
 * revision sistematica: seis obligatorios en casos de uso --- nombre, actores,
 * objetivo, flujo principal, precondiciones y postcondiciones --- y dos en
 * historias --- descripcion narrativa y criterios de aceptacion.</p>
 *
 * <p><strong>Esto no puede derivarse del texto y la interfaz no ofrece hacerlo
 * sin asistencia.</strong> Un requisito enuncia lo que el sistema debe hacer, es
 * decir, solo la respuesta del sistema. La accion del actor --- que abre la
 * pantalla, que aporta los datos, que confirma --- no esta en ningun requisito,
 * y una plantilla que la rellenara estaria inventando la interaccion.</p>
 *
 * <p>Lo que si se hace es marcar la procedencia: consta que el artefacto lo
 * redacto un modelo, para que quien lo lea sepa que ha de revisarlo y no lo
 * confunda con algo deducido de la especificacion.</p>
 */
public interface SpecificationGenerator {

	String CASO_DE_USO = "USE_CASE";
	String HISTORIA = "USER_STORY";

	/**
	 * Paso del flujo, a dos columnas.
	 *
	 * <p>La forma es la de las tablas 33 a 37 del manuscrito, que sigue el caso de
	 * uso expandido de Larman. Un paso puede tener solo una de las dos columnas:
	 * la persona actua una vez y el sistema hace varias cosas seguidas, y forzar
	 * ambas produciria acciones del actor que nadie realiza.</p>
	 */
	record Paso(int numero, String accionDelActor, String respuestaDelSistema, String referencia) {
	}

	/** Flujo alternativo o excepcional, colgado del paso del que se desvia. */
	record Desvio(String numero, String condicion, String respuesta, int desdeElPaso) {
	}

	/**
	 * Caso de uso expandido, con los campos de la tabla 8.
	 *
	 * @param relaciones        opcional: dependencias con otros casos de uso
	 * @param requisitosEspeciales opcional: restricciones no funcionales
	 * @param prioridad         opcional: importancia o frecuencia de uso
	 * @param riesgos           opcional: riesgos y consideraciones eticas
	 */
	record CasoDeUso(
			String nombre,
			String actorPrincipal,
			List<String> actoresSecundarios,
			String objetivo,
			List<String> precondiciones,
			List<Paso> flujoPrincipal,
			List<Desvio> flujosAlternativos,
			List<Desvio> flujosExcepcionales,
			String postcondicionExito,
			String postcondicionFracaso,
			String relaciones,
			String requisitosEspeciales,
			String prioridad,
			String riesgos) {
	}

	/**
	 * Historia de usuario, con los campos de la tabla 9.
	 *
	 * <p>La descripcion es el campo obligatorio y los tres de Connextra son
	 * opcionales por separado: se extraen de ella. Guardarlos aparte sirve para la
	 * trazabilidad; lo que se muestra es la narrativa.</p>
	 */
	record Historia(
			String descripcion,
			String criteriosDeAceptacion,
			String actor,
			String funcionalidad,
			String beneficio,
			String prioridad,
			String dependencias,
			String componentes,
			String valorDeNegocio) {
	}

	/** Lo generado, con lo que permite juzgarlo. */
	record Resultado(
			String kind,
			String nombre,
			/** Los campos, en el documento que se guardara. */
			String contenido,
			String fundamento,
			/** Lo que el modelo no pudo determinar y queda por decidir. */
			List<String> huecos,
			List<String> requisitos) {
	}

	/** Si hay un servicio de IA disponible para este proyecto. */
	boolean disponible();

	/**
	 * Genera a partir de uno o varios requisitos aprobados.
	 *
	 * <p>Un caso de uso puede salir de varios requisitos --- la tabla 8 admite
	 * "RF asociados" en plural --- mientras que una historia sale de uno.</p>
	 */
	List<Resultado> generar(List<RequirementInput> requisitos, String kind);
}
