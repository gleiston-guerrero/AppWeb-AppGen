package org.slcp.service.api;

import java.util.List;

/**
 * Informacion publica de la plataforma.
 *
 * <p>Realiza los requisitos FUN-01 y FUN-02: sin necesidad de iniciar sesion, la
 * plataforma presenta como esta construida, que es capaz de producir, cual es su
 * insumo de entrada, su autoria y su licencia.</p>
 *
 * @param name       nombre de la plataforma
 * @param version    version del servicio
 * @param purpose    que hace la plataforma, en una frase
 * @param input      cual es su materia prima
 * @param produces   que artefactos es capaz de producir
 * @param builtWith  con que esta construida
 * @param authorship autoria y adscripcion institucional
 * @param license    identificador SPDX de la licencia
 * @param repository direccion del repositorio publico
 */
public record PlatformInfo(
		String name,
		String version,
		String purpose,
		String input,
		List<String> produces,
		List<String> builtWith,
		String authorship,
		String license,
		String repository) {

	/** Informacion vigente de esta version de la plataforma. */
	public static PlatformInfo current(String version) {
		return new PlatformInfo(
				"SLCP",
				version,
				"Aplicacion web que genera aplicaciones web a partir de una especificacion de requisitos",
				"La especificacion de requisitos del software",
				List.of(
						"Estructura de proyecto y configuracion",
						"Esquema relacional y migraciones",
						"Contratos de servicio y capa de acceso a datos",
						"Codigo fuente de la logica de negocio",
						"Escenarios de aceptacion y casos de prueba",
						"Diagramas derivados del modelo",
						"Matriz de trazabilidad"),
				List.of(
						"Java 21",
						"Spring Boot 4.1",
						"Angular",
						"PostgreSQL"),
				"Gleiston Guerrero-Ulloa, Universidad Tecnica Estatal de Quevedo",
				"MIT",
				"https://github.com/gleiston-guerrero/AppWeb-AppGen");
	}
}
