package org.slcp.service.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Comprueba un caso de uso o una historia antes de guardarlos.
 *
 * <p>Se aplica a todos por igual: a lo generado, a lo editado y a lo escrito
 * desde cero. Lo escrito a mano no es mas fiable que lo generado --- solo tiene
 * otro autor ---, y comprobar unos si y otros no dejaria pasar precisamente los
 * que nadie reviso.</p>
 *
 * <p><strong>Nada de lo que se encuentre aqui impide guardar ni aceptar.</strong>
 * Convertir una advertencia en un veto pondria a la plataforma por encima de
 * quien responde del sistema. Lo que se hace es informar y dejar constancia de
 * lo que habia cuando se decidio.</p>
 *
 * <p>Aqui se comprueba la <strong>forma</strong>, que es lo que puede
 * establecerse sin conocer el dominio: que esten los campos obligatorios de las
 * tablas 8 y 9, que los flujos abran y cierren como deben, que cada excepcion
 * cuelgue de un paso que existe. La <strong>coherencia</strong> --- si el caso
 * de uso responde de verdad al requisito --- no se puede decidir con reglas, y
 * la juzga el modelo o una persona.</p>
 */
public final class SpecificationValidator {

	/**
	 * Reparo sobre un campo, con lo que lo motiva.
	 *
	 * <p>Un reparo grave no impide guardar ni aceptar: distingue lo que casi
	 * siempre conviene mirar de lo que solo a veces. <strong>La decision es del
	 * miembro del equipo</strong>, que puede tener razones que ninguna regla
	 * escrita de antemano conoce.</p>
	 */
	public record Reparo(String campo, String motivo, boolean grave) {
	}

	private SpecificationValidator() {
	}

	/** Comprueba segun la clase. */
	public static List<Reparo> revisar(Map<?, ?> campos, String kind) {
		return SpecificationGenerator.CASO_DE_USO.equals(kind)
				? casoDeUso(campos)
				: historia(campos);
	}

	// =================================================================

	/**
	 * Caso de uso: los seis obligatorios de la tabla 8, y la forma de Larman.
	 *
	 * <p>La tabla 8 marca obligatorios nombre, actores, objetivo, flujo principal,
	 * precondiciones y postcondiciones; los flujos alternativos aparecen al 73 por
	 * ciento y tambien como obligatorios en el manuscrito.</p>
	 */
	private static List<Reparo> casoDeUso(Map<?, ?> c) {
		List<Reparo> reparos = new ArrayList<>();

		exigir(c, "nombre", "El nombre identifica el caso de uso", reparos);
		exigir(c, "objetivo", "El objetivo dice que se consigue: sin el, el caso de uso no puede "
				+ "juzgarse", reparos);
		exigir(c, "postcondicionExito", "La postcondicion de exito dice en que queda el sistema",
				reparos);

		String actor = texto(c.get("actorPrincipal"));
		if (actor.isBlank()) {
			reparos.add(new Reparo("actorPrincipal",
					"Falta el actor principal, que la tabla 8 marca obligatorio", true));

		} else if (esElSistema(actor)) {
			// El sistema es la frontera de lo que se dibuja, no un participante.
			reparos.add(new Reparo("actorPrincipal",
					"El actor principal es el propio sistema. El sistema es la frontera del caso "
							+ "de uso, no un actor: revise quien lo inicia de verdad", true));
		}

		List<?> flujo = lista(c.get("flujoPrincipal"));
		if (flujo.isEmpty()) {
			reparos.add(new Reparo("flujoPrincipal",
					"El flujo principal esta vacio. Es el campo que hace expandido a un caso de "
							+ "uso", true));
			return reparos;
		}

		revisarFlujo(flujo, reparos);
		revisarDesvios(c, flujo.size(), reparos);
		revisarDiseno(flujo, reparos);

		return reparos;
	}

	private static void revisarFlujo(List<?> flujo, List<Reparo> reparos) {
		String primero = pasoCompleto(flujo.get(0));
		String ultimo = pasoCompleto(flujo.get(flujo.size() - 1));

		if (!minusculas(primero).contains("inicia cuando")) {
			reparos.add(new Reparo("flujoPrincipal",
					"El primer paso no dice cuando inicia el caso de uso. Sin el disparador, no se "
							+ "sabe que lo provoca", false));
		}

		if (!minusculas(ultimo).contains("termina cuando")) {
			reparos.add(new Reparo("flujoPrincipal",
					"El ultimo paso no dice cuando termina el caso de uso", false));
		}

		boolean hayComprobacion = false;
		for (Object paso : flujo) {
			String texto = minusculas(pasoCompleto(paso));

			if (texto.contains("comprueba") || texto.contains("valida") || texto.contains("verifica")) {
				hayComprobacion = true;
			}

			if (pasoCompleto(paso).isBlank()) {
				reparos.add(new Reparo("flujoPrincipal",
						"Hay un paso sin contenido en ninguna de las dos columnas", true));
			}
		}

		if (!hayComprobacion) {
			// Sin un paso de comprobacion, las excepciones no tienen de donde colgar,
			// y un caso de uso sin excepciones describe solo el dia bueno.
			reparos.add(new Reparo("flujoPrincipal",
					"Ningun paso comprueba nada. Sin un paso de comprobacion, las excepciones no "
							+ "tienen de donde salir y el caso de uso solo describe el camino que "
							+ "sale bien", false));
		}
	}

	/** Cada desvio ha de colgar de un paso que exista. */
	private static void revisarDesvios(Map<?, ?> c, int pasos, List<Reparo> reparos) {
		for (String campo : List.of("flujosAlternativos", "flujosExcepcionales")) {
			List<?> desvios = lista(c.get(campo));

			if ("flujosExcepcionales".equals(campo) && desvios.isEmpty()) {
				reparos.add(new Reparo(campo,
						"No hay ningun flujo de excepcion. Todo caso de uso real tiene al menos un "
								+ "camino que no alcanza el objetivo", false));
			}

			for (Object d : desvios) {
				if (!(d instanceof Map<?, ?> desvio)) {
					continue;
				}

				int desde = entero(desvio.get("desdeElPaso"));
				if (desde < 1 || desde > pasos) {
					reparos.add(new Reparo(campo,
							"El desvio " + texto(desvio.get("numero")) + " cuelga del paso " + desde
									+ ", que no existe: el flujo tiene " + pasos + " pasos", true));
				}

				if (texto(desvio.get("respuesta")).isBlank()) {
					reparos.add(new Reparo(campo,
							"El desvio " + texto(desvio.get("numero")) + " no dice que hace el "
									+ "sistema", true));
				}
			}
		}
	}

	/**
	 * El caso de uso no debe traer decisiones de diseno.
	 *
	 * <p>Un caso de uso ha de poder escribirse antes de saber si hay base de datos,
	 * cola o archivo. Si las menciona, las pruebas que salgan de el heredan esa
	 * decision sin que nadie la haya tomado.</p>
	 */
	private static void revisarDiseno(List<?> flujo, List<Reparo> reparos) {
		List<String> terminos = List.of("base de datos", "tabla ", "endpoint", "api ", "sql",
				"json", "cookie", "token", "servidor", "microservicio");

		for (Object paso : flujo) {
			String texto = minusculas(pasoCompleto(paso));

			for (String termino : terminos) {
				if (texto.contains(termino)) {
					reparos.add(new Reparo("flujoPrincipal",
							"Un paso menciona \"" + termino.trim() + "\", que es una decision de "
									+ "diseno. Un caso de uso describe que ocurre, no como se "
									+ "implementa", false));
					return;
				}
			}
		}
	}

	/** Historia: los dos obligatorios de la tabla 9, y la forma Connextra. */
	private static List<Reparo> historia(Map<?, ?> c) {
		List<Reparo> reparos = new ArrayList<>();

		String descripcion = texto(c.get("descripcion"));
		if (descripcion.isBlank()) {
			reparos.add(new Reparo("descripcion",
					"Falta la descripcion, unico campo presente en los ocho estudios de la tabla 9",
					true));
		} else {
			String plano = minusculas(descripcion);

			if (!plano.contains("como ") || !plano.contains("quiero ")) {
				reparos.add(new Reparo("descripcion",
						"La descripcion no sigue la forma Connextra: \"Como <rol>, quiero "
								+ "<funcionalidad>, para <beneficio>\"", false));
			}

			if (!plano.contains("para ")) {
				// Sin el porque, la historia no puede priorizarse: nadie sabe que se
				// pierde si no se hace.
				reparos.add(new Reparo("descripcion",
						"La descripcion no dice para que sirve. Sin el beneficio, la historia no "
								+ "puede priorizarse", false));
			}

			if (esElSistema(descripcion.replaceAll("(?i)^como\\s+", "").split(",")[0])) {
				reparos.add(new Reparo("descripcion",
						"El rol es el propio sistema. Una historia expresa lo que quiere una "
								+ "persona, no lo que hace el sistema", true));
			}
		}

		String criterios = texto(c.get("criteriosDeAceptacion"));
		if (criterios.isBlank()) {
			reparos.add(new Reparo("criteriosDeAceptacion",
					"Faltan los criterios de aceptacion, obligatorios segun la tabla 9", true));

		} else {
			String plano = minusculas(criterios);

			if (!plano.contains("dado") || !plano.contains("cuando") || !plano.contains("entonces")) {
				reparos.add(new Reparo("criteriosDeAceptacion",
						"Los criterios no siguen la forma Dado-Cuando-Entonces", false));
			}

			if (contar(plano, "escenario") < 2) {
				reparos.add(new Reparo("criteriosDeAceptacion",
						"Hay un solo escenario. Falta al menos uno que no alcance el objetivo: una "
								+ "historia con solo el camino bueno no esta especificada", false));
			}
		}

		return reparos;
	}

	// =================================================================

	private static void exigir(Map<?, ?> c, String campo, String porque, List<Reparo> reparos) {
		if (texto(c.get(campo)).isBlank()) {
			reparos.add(new Reparo(campo, "Falta " + campo + ". " + porque, true));
		}
	}

	private static boolean esElSistema(String texto) {
		String plano = minusculas(texto).replaceAll("^(el|la|los|las)\\s+", "").trim();

		return plano.startsWith("sistema") || plano.startsWith("plataforma")
				|| plano.startsWith("aplicacion") || plano.startsWith("aplicación")
				|| plano.startsWith("servicio");
	}

	private static String pasoCompleto(Object paso) {
		if (!(paso instanceof Map<?, ?> p)) {
			return texto(paso);
		}
		return (texto(p.get("accionDelActor")) + " " + texto(p.get("respuestaDelSistema"))).trim();
	}

	private static String texto(Object valor) {
		return valor == null ? "" : String.valueOf(valor).trim();
	}

	private static List<?> lista(Object valor) {
		return valor instanceof List<?> l ? l : List.of();
	}

	private static int entero(Object valor) {
		if (valor instanceof Number n) {
			return n.intValue();
		}
		try {
			return Integer.parseInt(texto(valor));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int contar(String texto, String parte) {
		int total = 0;
		int desde = 0;
		while ((desde = texto.indexOf(parte, desde)) >= 0) {
			total++;
			desde += parte.length();
		}
		return total;
	}

	private static String minusculas(String texto) {
		return texto == null ? "" : texto.toLowerCase(java.util.Locale.ROOT);
	}
}
