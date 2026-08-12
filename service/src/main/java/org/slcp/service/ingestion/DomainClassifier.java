package org.slcp.service.ingestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Separa los requisitos que son del proyecto de los que parecen de otro asunto,
 * y agrupa estos ultimos por lo que tratan.
 *
 * <p>Lo ajeno no entra, ni siquiera como borrador. Un requisito de otro sistema
 * metido en la lista contamina cuanto se calcule despues --- la validacion, la
 * deteccion de duplicados, el propio vocabulario del proyecto --- y quien lo
 * encuentre semanas mas tarde no sabra si estaba de mas o si el proyecto crecio.
 * Queda a la espera y decide una persona.</p>
 *
 * <p>La agrupacion existe porque decidir de uno en uno sobre treinta requisitos
 * ajenos no es decidir: es rendirse y aceptarlos todos. Agrupados por asunto,
 * la decision es sobre cuatro o cinco conjuntos y puede tomarse mirando.</p>
 */
public final class DomainClassifier {

	/**
	 * Proporcion minima del vocabulario del requisito que ha de conocer el
	 * proyecto para tenerlo por propio.
	 *
	 * <p>Ajustado enfrentando una especificacion de granja inteligente a
	 * requisitos de otros nueve asuntos, mas transversales y repetidos. Se prefiere
	 * pecar de retener: un requisito propio retenido cuesta un vistazo a quien lo
	 * subio, mientras que uno ajeno colado ensucia el proyecto sin que nadie lo
	 * advierta, y contamina cuanto se calcule despues.</p>
	 */
	public static final double UMBRAL_PROPIO = 0.30;

	/** Semejanza minima entre dos requisitos ajenos para agruparlos. */
	public static final double UMBRAL_GRUPO = 0.18;

	/**
	 * Vocabulario que no pertenece a ningun dominio en particular.
	 *
	 * <p>Sesiones, contrasenas, copias de seguridad, disponibilidad: son
	 * exigencias que valen para cualquier sistema. Tratarlas como ajenas seria
	 * equivocado, porque lo son de todos los dominios y por tanto tambien de
	 * este; tratarlas como propias sin mas las colaria en un proyecto que quiza
	 * no las quiere. Se separan y se decide sobre ellas.</p>
	 */
	// Las raices se escriben tal como las produce el reductor de
	// StatementSimilarity, no como se escribirian a mano: "teclado" se reduce a
	// "tecl", y guardar "teclad" hacia que nunca coincidiera.
	private static final Set<String> TRANSVERSAL = Set.of(
			"sesion", "contrasen", "clave", "usuari", "autentic", "acces", "permis",
			"perfil", "cifr", "segurid", "audit", "traz", "bitacor", "copi", "respald",
			"restaur", "disponib", "rendimient", "escalab", "accesib", "teclad",
			"idiom", "traduc", "privacid", "consentim", "personal", "factor", "verific",
			"dispositiv", "lector", "legibl", "maquin", "reconoc", "tecl",
			"disponibl", "servici", "operacion", "horari", "manten", "interrup",
			"tiempo de respuesta", "concurren", "carga");

	/** Requisitos del proyecto, transversales, y de otro asunto. */
	public record Reparto(
			List<Integer> propios,
			List<Grupo> transversales,
			List<Grupo> ajenos) {
	}

	/** Conjunto de requisitos ajenos que tratan de lo mismo. */
	public record Grupo(String etiqueta, List<String> terminos, List<Integer> indices) {
	}

	private DomainClassifier() {
	}

	/**
	 * Reparte los requisitos entrantes.
	 *
	 * @param existentes enunciados ya presentes en el proyecto
	 * @param entrantes  enunciados que llegan, en su orden
	 * @return los indices de los propios, y los ajenos agrupados por asunto
	 */
	public static Reparto repartir(List<String> existentes, List<String> entrantes) {
		Set<String> vocabulario = DomainCoherence.vocabulario(existentes);

		List<Integer> propios = new ArrayList<>();
		List<Integer> ajenos = new ArrayList<>();

		// Sin proyecto con que comparar, todo es propio: el primer documento define
		// el dominio y no puede ser ajeno a si mismo.
		if (existentes.size() < DomainCoherence.MINIMO_PARA_JUZGAR) {
			for (int i = 0; i < entrantes.size(); i++) {
				propios.add(i);
			}
			return new Reparto(propios, List.of(), List.of());
		}

		List<Integer> transversales = new ArrayList<>();

		for (int i = 0; i < entrantes.size(); i++) {
			String enunciado = entrantes.get(i);

			// El examen de lo transversal va primero: un requisito de sesiones o de
			// copias de seguridad puede compartir vocabulario con el proyecto por
			// casualidad, y clasificarlo como propio lo colaria sin decision.
			if (esTransversal(enunciado)) {
				transversales.add(i);
			} else if (esPropio(vocabulario, enunciado)) {
				propios.add(i);
			} else {
				ajenos.add(i);
			}
		}

		return new Reparto(propios, agrupar(entrantes, transversales),
				agrupar(entrantes, ajenos));
	}

	/**
	 * Indica si el requisito vale para cualquier sistema.
	 *
	 * <p>Se exige que una parte apreciable de su vocabulario sea de esa clase, no
	 * un termino suelto: cualquier requisito puede mencionar una pantalla sin ser
	 * por ello una exigencia de interfaz.</p>
	 */
	static boolean esTransversal(String enunciado) {
		Set<String> unicos = new HashSet<>(StatementSimilarity.tokens(enunciado));
		if (unicos.size() < 4) {
			return false;
		}

		// Se compara por raices en ambos sentidos: la reduccion de "disponibilidad"
		// y la de "disponible" no coinciden entre si, y exigir que la palabra empiece
		// por la del lexico dejaria fuera la mitad de las formas.
		// La coincidencia en sentido inverso --- que la raiz del lexico empiece por
		// la palabra --- solo se admite con palabras largas. Sin esa condicion,
		// "hora" casaria con "horario" y una reserva de restaurante pasaria por
		// exigencia de disponibilidad.
		long comunes = unicos.stream()
				.filter(t -> TRANSVERSAL.stream().anyMatch(raiz ->
						t.startsWith(raiz) || (t.length() >= 6 && raiz.startsWith(t))))
				.count();

		// Basta una parte pequena del vocabulario: estos requisitos suelen ser
		// breves, y exigir mas dejaria fuera los que solo nombran su asunto una vez,
		// como una exigencia de disponibilidad o de tiempo de respuesta.
		return (double) comunes / unicos.size() >= 0.15;
	}

	/** Indica si el proyecto ya hablaba de lo que este requisito trata. */
	static boolean esPropio(Set<String> vocabulario, String enunciado) {
		List<String> tokens = StatementSimilarity.tokens(enunciado);
		if (tokens.isEmpty()) {
			return true;
		}

		Set<String> unicos = new HashSet<>(tokens);
		long conocidos = unicos.stream().filter(vocabulario::contains).count();

		return (double) conocidos / unicos.size() >= UMBRAL_PROPIO;
	}

	/**
	 * Agrupa los ajenos por lo que tratan.
	 *
	 * <p>Se emplea agrupacion codiciosa: cada requisito entra en el primer grupo
	 * al que se parece bastante, y si no se parece a ninguno abre uno nuevo. No
	 * busca la particion optima, y no hace falta: el resultado lo revisa una
	 * persona, a la que le basta con que lo del mismo asunto quede junto.</p>
	 */
	static List<Grupo> agrupar(List<String> enunciados, List<Integer> indices) {
		List<List<Integer>> grupos = new ArrayList<>();

		for (int indice : indices) {
			String enunciado = enunciados.get(indice);
			List<Integer> destino = null;

			for (List<Integer> grupo : grupos) {
				double maxima = 0;
				for (int miembro : grupo) {
					maxima = Math.max(maxima, StatementSimilarity.estricta(enunciado,
							enunciados.get(miembro)));
				}
				if (maxima >= UMBRAL_GRUPO) {
					destino = grupo;
					break;
				}
			}

			if (destino == null) {
				destino = new ArrayList<>();
				grupos.add(destino);
			}
			destino.add(indice);
		}

		List<Grupo> salida = new ArrayList<>();
		for (List<Integer> grupo : grupos) {
			List<String> terminos = terminosCaracteristicos(enunciados, grupo);
			salida.add(new Grupo(etiquetaDe(terminos), terminos, grupo));
		}

		// Los grupos grandes primero: son los que mas informan y sobre los que
		// antes conviene decidir.
		salida.sort(Comparator.comparingInt((Grupo g) -> g.indices().size()).reversed());
		return salida;
	}

	/** Terminos que mas se repiten dentro del grupo, que es lo que lo caracteriza. */
	static List<String> terminosCaracteristicos(List<String> enunciados, List<Integer> grupo) {
		Map<String, Integer> cuenta = new HashMap<>();

		for (int indice : grupo) {
			for (String termino : new HashSet<>(StatementSimilarity.tokens(enunciados.get(indice)))) {
				cuenta.merge(termino, 1, Integer::sum);
			}
		}

		List<Map.Entry<String, Integer>> ordenados = new ArrayList<>(cuenta.entrySet());
		ordenados.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
				.thenComparing(Map.Entry.comparingByKey()));

		List<String> terminos = new ArrayList<>();
		for (Map.Entry<String, Integer> e : ordenados) {
			// Un termino que aparece una sola vez en el grupo no lo caracteriza,
			// salvo que el grupo sea de uno solo.
			if (e.getValue() < 2 && grupo.size() > 1) {
				continue;
			}
			terminos.add(e.getKey());
			if (terminos.size() == 5) {
				break;
			}
		}
		return terminos;
	}

	/**
	 * Etiqueta legible del grupo.
	 *
	 * <p>Se forma con sus terminos y se presenta como conjetura. Nombrar el
	 * dominio con acierto exigiria conocerlo; lo que aqui se sabe es que palabras
	 * comparten esos requisitos, y eso es lo que se dice.</p>
	 */
	static String etiquetaDe(List<String> terminos) {
		if (terminos.isEmpty()) {
			return "Sin vocabulario comun";
		}
		return String.join(", ", terminos.subList(0, Math.min(3, terminos.size())));
	}
}
