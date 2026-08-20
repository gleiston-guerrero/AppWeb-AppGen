package org.slcp.service.generation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifica los actores de caso de uso en el texto de un requisito.
 *
 * <p>El actor de un caso de uso no es lo mismo que el interesado ni la fuente
 * que a veces trae la especificacion: aquellos dicen de donde viene el requisito
 * o a quien le importa, y el actor dice quien ejerce lo que el sistema hace.</p>
 *
 * <p><strong>El sistema no es un actor.</strong> Es la frontera de lo que se
 * dibuja, y casi todos los enunciados lo ponen como sujeto --- "el sistema
 * debera registrar" ---, de modo que tomar el sujeto sin mas daria un diagrama
 * con un unico actor llamado sistema y ningun caso de uso util.</p>
 *
 * <p>Lo que se busca entonces es a quien va dirigido lo que el sistema hace, o
 * quien lo desencadena. Cuando el enunciado no lo dice, aqui no se inventa: se
 * declara que no se pudo identificar, y esa es la senal de que hace falta una
 * persona o un modelo que conozca el dominio.</p>
 */
public final class ActorExtractor {

	/** Cuando no puede identificarse a partir del texto. */
	public static final String SIN_IDENTIFICAR = "Sin identificar";

	/**
	 * Sujetos que son el propio sistema, no un actor.
	 *
	 * <p>Se reconocen para descartarlos, no para dibujarlos.</p>
	 */
	private static final Set<String> ES_EL_SISTEMA = Set.of(
			"sistema", "plataforma", "aplicacion", "servicio", "software", "programa");

	/** El sujeto de la obligacion: "el X debera…". */
	/** Letras del castellano: sin las acentuadas, "explotación" se corta a medias. */
	private static final String LETRA = "a-záéíóúüñ";

	private static final Pattern SUJETO = Pattern.compile(
			"(?:^|[,.]\\s*|\\by\\s+)(?:el|la|los|las)\\s+([" + LETRA + "][" + LETRA + " ]{2,45}?)\\s+deber",
			Pattern.CASE_INSENSITIVE);

	/**
	 * A quien va dirigido lo que el sistema hace.
	 *
	 * <p>"notificar al responsable", "mostrar al viajero", "permitir al socio".
	 * Es el actor cuando el sujeto es el propio sistema.</p>
	 */
	private static final Pattern DESTINATARIO = Pattern.compile(
			"\\b(?:notificar|avisar|informar|mostrar|permitir|advertir|entregar|presentar|"
					+ "devolver|ofrecer|comunicar|recordar)\\s+(?:al|a la|a los|a las)\\s+"
					+ "([" + LETRA + "]+(?:\\s+(?:de|del|de la)\\s+[" + LETRA + "]+)?"
					+ "(?:\\s+[" + LETRA + "]+)?)",
			Pattern.CASE_INSENSITIVE);

	/** Quien decide o indica algo: tambien ejerce, aunque no sea el sujeto. */
	private static final Pattern QUIEN_INDICA = Pattern.compile(
			"\\bque\\s+(?:indique|elija|solicite|seleccione|decida|configure|establezca|"
					+ "determine|pida|lo solicite)\\s+(?:el|la|los|las)\\s+"
					+ "([" + LETRA + "]+(?:\\s+(?:de|del|de la)\\s+[" + LETRA + "]+)?"
					+ "(?:\\s+[" + LETRA + "]+)?)",
			Pattern.CASE_INSENSITIVE);

	private ActorExtractor() {
	}

	/** Actor identificado en un requisito, con lo que lo justifica. */
	public record Identificado(String actor, String porque, boolean seguro) {
	}

	/**
	 * Identifica los actores de un enunciado.
	 *
	 * <p>Se devuelven todos los que aparezcan: un requisito puede implicar a dos
	 * --- quien pide algo y quien lo recibe --- y quedarse con uno perderia la
	 * mitad del caso de uso.</p>
	 */
	/**
	 * Identifica los actores, prefiriendo el caso de uso si lo hay.
	 *
	 * <p>Un caso de uso aceptado declara su actor principal: no hay que deducirlo
	 * del enunciado ni acertar con una regla. Es una decision que alguien tomo, y
	 * usarla es preferible a repetir la conjetura.</p>
	 */
	public static List<Identificado> identificar(String enunciado, String casoDeUso) {
		if (casoDeUso != null && !casoDeUso.isBlank()) {
			List<Identificado> declarados = delCasoDeUso(casoDeUso);
			if (!declarados.isEmpty()) {
				return declarados;
			}
		}
		return identificar(enunciado);
	}

	/** Actor principal y secundarios que el caso de uso declara. */
	private static List<Identificado> delCasoDeUso(String casoDeUso) {
		List<Identificado> salida = new ArrayList<>();
		Set<String> vistos = new HashSet<>();

		Matcher principal = Pattern.compile("\"actorPrincipal\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(casoDeUso);

		if (principal.find()) {
			String actor = limpiar(principal.group(1));
			if (!esElSistema(actor) && vistos.add(clave(actor))) {
				salida.add(new Identificado(actor,
						"Lo declara el caso de uso aceptado como actor principal", true));
			}
		}

		Matcher secundarios = Pattern.compile(
				"\"actoresSecundarios\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(casoDeUso);

		if (secundarios.find()) {
			Matcher uno = Pattern.compile("\"([^\"]+)\"").matcher(secundarios.group(1));
			while (uno.find()) {
				String actor = limpiar(uno.group(1));
				if (!esElSistema(actor) && vistos.add(clave(actor))) {
					salida.add(new Identificado(actor,
							"Lo declara el caso de uso aceptado como actor secundario", true));
				}
			}
		}
		return salida;
	}

	public static List<Identificado> identificar(String enunciado) {
		if (enunciado == null || enunciado.isBlank()) {
			return List.of(new Identificado(SIN_IDENTIFICAR, "El requisito no tiene enunciado", false));
		}

		List<Identificado> salida = new ArrayList<>();
		Set<String> vistos = new LinkedHashSet<>();

		// 1. El sujeto de la obligacion, si no es el propio sistema.
		Matcher sujeto = SUJETO.matcher(enunciado);
		while (sujeto.find()) {
			String candidato = limpiar(sujeto.group(1));

			if (!esElSistema(candidato) && vistos.add(clave(candidato))) {
				salida.add(new Identificado(candidato,
						"Es quien el enunciado obliga a actuar: \"" + candidato + " debera…\"", true));
			}
		}

		// 2. A quien va dirigido lo que el sistema hace.
		anadir(DESTINATARIO.matcher(enunciado), salida, vistos,
				"El sistema dirige a esta persona lo que hace, de modo que es quien lo recibe");

		// 3. Quien decide o indica algo dentro del enunciado.
		anadir(QUIEN_INDICA.matcher(enunciado), salida, vistos,
				"El enunciado dice que esta persona indica o elige algo, de modo que interviene");

		if (salida.isEmpty()) {
			return List.of(new Identificado(SIN_IDENTIFICAR,
					"El enunciado solo obliga al sistema y no dice a quien sirve ni quien lo "
							+ "desencadena. Identificarlo exige conocer el dominio: eso lo decide "
							+ "una persona, o un modelo que lo conozca",
					false));
		}
		return salida;
	}

	/** Si de este requisito puede salir un caso de uso con actor. */
	public static boolean tieneActor(String enunciado) {
		return identificar(enunciado).stream().anyMatch(Identificado::seguro);
	}

	// =================================================================

	private static void anadir(Matcher m, List<Identificado> salida, Set<String> vistos,
			String porque) {

		while (m.find()) {
			String candidato = limpiar(m.group(1));

			if (!esElSistema(candidato) && candidato.length() > 3 && vistos.add(clave(candidato))) {
				salida.add(new Identificado(candidato, porque, true));
			}
		}
	}

	/**
	 * Descarta lo que en realidad es el sistema.
	 *
	 * <p>Se compara la primera palabra: "sistema de riego" y "plataforma" son el
	 * sistema, y dibujarlos como actor convertiria la frontera en participante.</p>
	 */
	private static boolean esElSistema(String candidato) {
		String primera = normalizar(candidato).split(" ")[0];
		return ES_EL_SISTEMA.contains(primera);
	}

	/** Verbos que pueden colarse al final del grupo capturado. */
	private static final Pattern INFINITIVO_FINAL = Pattern.compile(
			"\\s+[" + LETRA + "]+(?:ar|er|ir)$", Pattern.CASE_INSENSITIVE);

	private static String limpiar(String texto) {
		String t = texto.trim().replaceAll("\\s+", " ");

		// "permitir al socio reservar" captura tambien el infinitivo, que no es
		// parte del nombre del actor.
		t = INFINITIVO_FINAL.matcher(t).replaceAll("");

		// Un actor es un sustantivo, no una oracion: si trae subordinada, se corta.
		int corte = normalizar(t).indexOf(" que ");
		if (corte > 0) {
			t = t.substring(0, corte);
		}
		return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
	}

	private static String clave(String texto) {
		return normalizar(texto);
	}

	private static String normalizar(String texto) {
		String base = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
	}
}
