package org.slcp.service.ingestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detecta que un conjunto de requisitos no trata del mismo asunto que otro.
 *
 * <p>Una especificacion habla de un dominio, y su vocabulario lo delata: parcelas,
 * sensores, riego. Cuando lo que llega no comparte practicamente ningun termino
 * propio con lo que ya hay, lo mas probable es que se haya subido el documento
 * equivocado, o que se esten mezclando dos sistemas en un mismo proyecto.</p>
 *
 * <p>No se impide: se advierte. Hay proyectos legitimamente amplios, y hay
 * primeros documentos que no se parecen a nada porque no habia nada. Decidir por
 * quien sube el documento seria arrogarse un juicio sobre el alcance del proyecto
 * que corresponde a quien responde de el.</p>
 */
public final class DomainCoherence {

	/**
	 * Por debajo de esta proporcion de terminos compartidos, se advierte.
	 *
	 * <p>Se mide que proporcion del vocabulario de lo que llega aparece tambien en
	 * lo que ya hay. No se exige que los terminos se repitan dentro de cada
	 * conjunto: probado con dos especificaciones distintas del mismo sistema, esa
	 * exigencia dejaba fuera casi todo el vocabulario compartido y avisaba de
	 * documentos que si eran del mismo dominio.</p>
	 */
	public static final double UMBRAL_DOMINIO = 0.20;

	/** Minimo de requisitos a cada lado para que la medida signifique algo. */
	public static final int MINIMO_PARA_JUZGAR = 3;

	/** Resultado del examen. */
	public record Veredicto(
			boolean aviso,
			double coincidencia,
			List<String> terminosCompartidos,
			List<String> terminosDeLoQueLlega,
			String explicacion) {
	}

	private DomainCoherence() {
	}

	/**
	 * Examina si lo que llega trata del mismo asunto que lo que ya hay.
	 *
	 * @param existentes enunciados ya presentes en el proyecto
	 * @param entrantes  enunciados del documento que se importa
	 */
	public static Veredicto examinar(List<String> existentes, List<String> entrantes) {
		if (existentes.size() < MINIMO_PARA_JUZGAR || entrantes.size() < MINIMO_PARA_JUZGAR) {
			return new Veredicto(false, 1.0, List.of(), List.of(),
					"No hay bastantes requisitos a ambos lados para juzgar el dominio.");
		}

		Set<String> vocabularioExistente = vocabulario(existentes);
		Set<String> vocabularioEntrante = vocabulario(entrantes);

		if (vocabularioExistente.isEmpty() || vocabularioEntrante.isEmpty()) {
			return new Veredicto(false, 1.0, List.of(), List.of(),
					"No hay vocabulario bastante para juzgar el dominio.");
		}

		Set<String> compartidos = new HashSet<>(vocabularioEntrante);
		compartidos.retainAll(vocabularioExistente);

		// Que proporcion de lo que llega ya se hablaba aqui. Se divide por lo
		// entrante y no por la union: un proyecto grande tiene mucho vocabulario, y
		// dividir por la union haria que cualquier documento nuevo pareciese ajeno
		// solo por ser mas breve.
		double coincidencia = (double) compartidos.size() / vocabularioEntrante.size();

		Set<String> soloEntrantes = new HashSet<>(vocabularioEntrante);
		soloEntrantes.removeAll(vocabularioExistente);

		boolean aviso = coincidencia < UMBRAL_DOMINIO;

		String explicacion = aviso
				? "Lo que llega apenas comparte vocabulario con lo que ya hay en el proyecto: "
						+ porcentaje(coincidencia) + " de terminos en comun. Puede tratarse de otro "
						+ "sistema, o de un documento subido por equivocacion"
				: "Lo que llega comparte " + porcentaje(coincidencia)
						+ " de su vocabulario propio con lo que ya hay";

		return new Veredicto(aviso, redondear(coincidencia),
				ordenados(compartidos), ordenados(soloEntrantes), explicacion);
	}

	/**
	 * Examina un solo requisito frente al proyecto.
	 *
	 * <p>Con uno solo la medida es mas fragil, de modo que se exige que no comparta
	 * ningun termino propio para advertir. Un requisito breve puede no repetir el
	 * vocabulario del proyecto sin ser ajeno a el.</p>
	 */
	public static Veredicto examinarUno(List<String> existentes, String entrante) {
		if (existentes.size() < MINIMO_PARA_JUZGAR) {
			return new Veredicto(false, 1.0, List.of(), List.of(),
					"No hay bastantes requisitos en el proyecto para juzgar el dominio.");
		}

		Set<String> propios = vocabulario(existentes);
		Set<String> palabras = new HashSet<>(StatementSimilarity.tokens(entrante));

		Set<String> compartidos = new HashSet<>(propios);
		compartidos.retainAll(palabras);

		boolean aviso = compartidos.isEmpty() && palabras.size() >= 4;

		return new Veredicto(aviso, compartidos.isEmpty() ? 0.0 : 1.0,
				ordenados(compartidos), List.of(),
				aviso
						? "Este requisito no comparte ningun termino con el vocabulario del proyecto. "
								+ "Compruebe que corresponde a este sistema"
						: "El requisito emplea vocabulario del proyecto");
	}

	/** Vocabulario significativo de un conjunto de enunciados. */
	static Set<String> vocabulario(List<String> enunciados) {
		Set<String> terminos = new HashSet<>();
		for (String enunciado : enunciados) {
			terminos.addAll(StatementSimilarity.tokens(enunciado));
		}
		return terminos;
	}

	private static List<String> ordenados(Set<String> terminos) {
		List<String> lista = new ArrayList<>(terminos);
		lista.sort(Comparator.naturalOrder());
		return lista.size() > 12 ? lista.subList(0, 12) : lista;
	}

	private static String porcentaje(double valor) {
		return Math.round(valor * 100) + " por ciento";
	}

	private static double redondear(double valor) {
		return Math.round(valor * 100.0) / 100.0;
	}
}
