package org.slcp.service.ingestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Propone criterios de verificacion para requisitos que carecen de ellos.
 *
 * <p>Realiza ANA-18 y ANA-20. El limite no esta entre redactar y preguntar sino
 * entre lo observable, que el enunciado contiene, y la magnitud, que solo tiene
 * quien responde del sistema. De <em>el sistema debera registrar la mascota</em>
 * se sigue sin esfuerzo el criterio; de <em>debera responder con rapidez</em> no
 * se sigue que dos segundos basten.</p>
 *
 * <p>Por eso, donde hace falta una cifra, se deja un hueco marcado en lugar de
 * inventarla. Un numero inventado por la plataforma acaba pareciendo una
 * decision de la institucion en cuanto alguien lo acepta sin mirar.</p>
 *
 * <p>Esta implementacion es determinista y no depende de servicio externo
 * alguno, conforme a ANA-06. La generacion asistida se conecta como otra
 * implementacion de {@link CriterionSuggester} sin alterar lo demas.</p>
 */
public final class RuleBasedCriterionSuggester implements CriterionSuggester {

	/** Marca del hueco que debe rellenar una persona. */
	public static final String HUECO = "[indique el valor]";

	/**
	 * Verbos cuyo cumplimiento puede observarse.
	 *
	 * <p>La lista es corta a proposito. Ampliarla con verbos vagos --- gestionar,
	 * administrar, soportar --- produciria propuestas para requisitos que no
	 * describen una accion observable, y esas propuestas serian tan vagas como el
	 * requisito que pretenden verificar.</p>
	 */
	private static final List<String> VERBOS_OBSERVABLES = List.of(
			"registrar", "almacenar", "guardar", "consultar", "mostrar", "listar", "buscar",
			"filtrar", "generar", "emitir", "enviar", "notificar", "validar", "verificar",
			"calcular", "actualizar", "modificar", "eliminar", "exportar", "importar",
			"responder", "autenticar", "autorizar", "cifrar", "publicar", "aprobar", "rechazar",
			"asignar", "cargar", "descargar", "recuperar", "bloquear", "cancelar");

	/** Valor que devuelve la busqueda cuando no reconoce ningun verbo. */
	private static final String SIN_ACCION = "";

	@Override
	public List<Suggestion> proponer(String enunciado, boolean requiereMagnitud) {
		List<Suggestion> propuestas = new ArrayList<>();
		if (enunciado == null || enunciado.isBlank()) {
			return propuestas;
		}

		String accion = accionDe(enunciado);
		if (accion.isEmpty()) {
			// Sin accion observable no hay nada que derivar. Devolver una propuesta
			// generica seria peor que no devolver ninguna: parece una respuesta, se
			// acepta sin leerla, y el requisito acaba con un criterio que no verifica
			// nada. Quien revisa recibira la peticion de escribirlo.
			return propuestas;
		}
		String objeto = objetoDe(enunciado);

		// Primera: la observacion directa, derivada del propio enunciado.
		propuestas.add(new Suggestion(
				"Con datos validos, " + accion + " " + objeto
						+ " y comprobar que el resultado queda registrado y puede consultarse despues.",
				"Derivada del enunciado: recoge la accion y su objeto, y anade la observacion "
						+ "que permite comprobarla.",
				false));

		// Segunda: el caso adverso, que suele quedar sin especificar.
		propuestas.add(new Suggestion(
				"Con datos invalidos o incompletos, intentar " + accion + " " + objeto
						+ " y comprobar que el sistema lo rechaza e indica que corregir.",
				"Un criterio que solo comprueba el caso favorable deja sin verificar la mitad del "
						+ "comportamiento.",
				false));

		// Tercera: con magnitud, solo si el requisito la exige.
		if (requiereMagnitud) {
			propuestas.add(new Suggestion(
					"Con " + HUECO + " de carga, medir el tiempo de " + accion + " " + objeto
							+ " y comprobar que no supera " + HUECO + ".",
					"Este requisito exige una magnitud. La plataforma no la propone: que valor "
							+ "basta depende del riesgo que se considere tolerable.",
					true));
		}

		return propuestas;
	}

	/** Primer verbo observable que aparece en el enunciado. */
	private String accionDe(String enunciado) {
		String texto = normalizar(enunciado);
		for (String verbo : VERBOS_OBSERVABLES) {
			if (texto.contains(verbo)) {
				return verbo;
			}
		}
		return SIN_ACCION;
	}

	/**
	 * Fragmento del enunciado que sigue al verbo.
	 *
	 * <p>Es una aproximacion deliberadamente sencilla. Errar aqui produce una
	 * propuesta torpe que quien revisa corrige o descarta; un analisis mas
	 * ambicioso producirla mas convincente y no necesariamente mas acertada, que
	 * es peor.</p>
	 */
	private String objetoDe(String enunciado) {
		String texto = normalizar(enunciado);
		String accion = accionDe(texto);

		int desde = texto.indexOf(accion);
		if (desde < 0) {
			return "lo indicado";
		}
		String resto = texto.substring(desde + accion.length()).trim();
		if (resto.isEmpty()) {
			return "lo indicado";
		}

		// Se corta en la primera marca que introduce una enumeracion o una
		// subordinada: continuar produce un objeto largo que al insertarse en la
		// plantilla da una frase que no se sostiene.
		for (String corte : List.of(",", ";", " ingresando ", " incluyendo ", " con ", " que ", " para ")) {
			int donde = resto.indexOf(corte);
			if (donde > 0) {
				resto = resto.substring(0, donde);
			}
		}

		String[] palabras = resto.trim().split("\\s+");
		int cuantas = Math.min(palabras.length, 6);
		return String.join(" ", List.of(palabras).subList(0, cuantas)).replaceAll("[.,;:]$", "").trim();
	}

	private static String normalizar(String texto) {
		String base = texto.trim().toLowerCase(Locale.ROOT);
		base = Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		return base.replaceAll("\\s+", " ");
	}
}
