package org.slcp.service.generation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Genera diagramas derivandolos del conjunto de requisitos.
 *
 * <p>Se escriben en Mermaid: es texto, de modo que viaja en la base de datos,
 * se compara entre versiones y se lee sin herramienta. Un diagrama guardado como
 * imagen no puede compararse ni regenerarse cuando el requisito cambia.</p>
 *
 * <p>Aqui se dibuja lo que los requisitos ya dicen: quien actua, sobre que, y
 * que estados se nombran. Lo que no dicen no se dibuja: un diagrama con cajas
 * inventadas parece conocimiento y es conjetura.</p>
 */
public final class DerivedDiagramGenerator implements DiagramGenerator {

	public static final String CASOS_DE_USO = "USE_CASE";
	public static final String ESTADOS = "STATE";
	public static final String CONTEXTO = "CONTEXT";
	public static final String TRAZABILIDAD = "TRACEABILITY";

	/** Verbos que anuncian un cambio de estado en el enunciado. */
	private static final List<String> VERBOS_DE_ESTADO = List.of(
			"activar", "cerrar", "abrir", "aprobar", "rechazar", "registrar", "cancelar",
			"suspender", "reanudar", "bloquear", "desbloquear", "aceptar", "anular");

	@Override
	public List<String> clases() {
		return List.of(CASOS_DE_USO, ESTADOS, CONTEXTO, TRAZABILIDAD);
	}

	@Override
	public List<ArtifactProposal> generar(List<RequirementInput> requisitos, String clase) {
		if (requisitos.isEmpty()) {
			return List.of();
		}

		return switch (clase) {
			case CASOS_DE_USO -> List.of(casosDeUso(requisitos));
			case ESTADOS -> estados(requisitos);
			case CONTEXTO -> List.of(contexto(requisitos));
			case TRAZABILIDAD -> List.of(trazabilidad(requisitos));
			default -> List.of();
		};
	}

	// =================================================================

	/**
	 * Casos de uso: quien actua sobre que.
	 *
	 * <p>Los requisitos sin actor declarado van a un actor "sin declarar", que se
	 * ve en el dibujo. Omitirlos daria un diagrama mas limpio y ocultaria una
	 * carencia de la especificacion.</p>
	 */
	private ArtifactProposal casosDeUso(List<RequirementInput> requisitos) {
		Map<String, List<RequirementInput>> porActor = new LinkedHashMap<>();
		int sinActor = 0;

		for (RequirementInput r : requisitos) {
			// El actor sale del enunciado, no del campo "actor" de la especificacion:
			// ese trae el interesado o la fuente --- quien pidio el requisito o de
			// donde vienen sus datos --- y no quien ejerce el caso de uso.
			List<ActorExtractor.Identificado> identificados =
					ActorExtractor.identificar(r.statement(), r.useCase());

			boolean alguno = false;
			for (ActorExtractor.Identificado i : identificados) {
				if (i.seguro()) {
					porActor.computeIfAbsent(i.actor(), k -> new ArrayList<>()).add(r);
					alguno = true;
				}
			}

			if (!alguno) {
				sinActor++;
				porActor.computeIfAbsent(ActorExtractor.SIN_IDENTIFICAR, k -> new ArrayList<>())
						.add(r);
			}
		}

		StringBuilder m = new StringBuilder();
		m.append("graph LR\n");

		int i = 0;
		for (Map.Entry<String, List<RequirementInput>> e : porActor.entrySet()) {
			String idActor = "A" + (i++);
			m.append("  ").append(idActor).append("([\"").append(escapar(e.getKey())).append("\"])\n");

			for (RequirementInput r : e.getValue()) {
				String idCaso = "C" + identificador(r.readableId());
				m.append("  ").append(idCaso).append("[\"").append(escapar(r.etiqueta()))
						.append("<br/>").append(escapar(recortar(nombreDe(r), 40))).append("\"]\n");
				m.append("  ").append(idActor).append(" --> ").append(idCaso).append('\n');
			}
		}

		return new ArtifactProposal(CASOS_DE_USO,
				"Casos de uso del proyecto",
				m.toString(),
				"MERMAID",
				"Un caso por requisito aprobado, agrupado por el actor que el propio requisito "
						+ "declara"
						+ (sinActor > 0
								? ". En " + sinActor + " requisitos el enunciado solo obliga al sistema y no "
								+ "dice a quien sirve: aparecen bajo \"sin identificar\". Averiguarlo "
								+ "exige conocer el dominio, y por eso conviene generarlo con "
								+ "asistencia o decidirlo a mano"
								: ""),
				sinActor > 0,
				requisitos.stream().map(RequirementInput::readableId).toList());
	}

	/**
	 * Estados: para los requisitos que nombran transiciones.
	 *
	 * <p>Se genera uno por cada requisito que enuncia una condicion y una accion
	 * de cambio de estado. No se junta todo en un diagrama porque distintos
	 * requisitos hablan de cosas distintas, y unirlas sugeriria una maquina de
	 * estados unica que nadie ha decidido.</p>
	 */
	private List<ArtifactProposal> estados(List<RequirementInput> requisitos) {
		List<ArtifactProposal> salida = new ArrayList<>();

		for (RequirementInput r : requisitos) {
			String plano = normalizar(r.statement());
			String verbo = VERBOS_DE_ESTADO.stream().filter(plano::contains).findFirst().orElse(null);

			boolean tieneCondicion = plano.contains("cuando ") || plano.contains("si ")
					|| plano.contains("siempre que ");

			if (verbo == null || !tieneCondicion) {
				continue;
			}

			StringBuilder m = new StringBuilder();
			m.append("stateDiagram-v2\n");
			m.append("  [*] --> EnEspera\n");
			m.append("  EnEspera --> Activado : ").append(escapar(condicionDe(r.statement()))).append('\n');
			m.append("  Activado --> EnEspera : ").append("[indique la condicion de vuelta]").append('\n');
			m.append("  note right of Activado\n");
			m.append("    ").append(escapar(recortar(r.etiqueta() + " — " + nombreDe(r), 60))).append('\n');
			m.append("  end note\n");

			salida.add(new ArtifactProposal(ESTADOS,
					"Estados de " + r.etiqueta(),
					m.toString(),
					"MERMAID",
					"El requisito enuncia una condicion y una accion de cambio, de modo que hay dos "
							+ "estados y una transicion. La condicion de vuelta no la dice el requisito "
							+ "y queda como hueco: es la carencia mas frecuente en los requisitos que "
							+ "activan algo",
					true,
					List.of(r.readableId())));

			if (salida.size() == 8) {
				break;
			}
		}
		return salida;
	}

	/** Contexto: los actores y el sistema, sin entrar en el detalle. */
	private ArtifactProposal contexto(List<RequirementInput> requisitos) {
		Set<String> actores = new LinkedHashSet<>();
		for (RequirementInput r : requisitos) {
			for (ActorExtractor.Identificado i : ActorExtractor.identificar(r.statement(),
					r.useCase())) {
				if (i.seguro()) {
					actores.add(i.actor());
				}
			}
		}

		StringBuilder m = new StringBuilder();
		m.append("graph TD\n");
		m.append("  S[\"El sistema\"]\n");

		int i = 0;
		for (String actor : actores) {
			String id = "E" + (i++);
			m.append("  ").append(id).append("([\"").append(escapar(actor)).append("\"])\n");
			m.append("  ").append(id).append(" <--> S\n");
		}

		return new ArtifactProposal(CONTEXTO,
				"Contexto del sistema",
				m.toString(),
				"MERMAID",
				"Los actores se identifican en los enunciados de los requisitos aprobados: "
						+ actores.size() + " distintos. Lo que hay al otro lado de cada uno no lo "
						+ "dicen los requisitos y no se dibuja",
				actores.isEmpty(),
				requisitos.stream().map(RequirementInput::readableId).toList());
	}

	/** Trazabilidad: requisitos agrupados por su familia de origen. */
	private ArtifactProposal trazabilidad(List<RequirementInput> requisitos) {
		Map<String, List<RequirementInput>> porFamilia = new LinkedHashMap<>();

		for (RequirementInput r : requisitos) {
			String familia = r.etiqueta().replaceAll("[-_]?\\d+.*$", "");
			porFamilia.computeIfAbsent(familia.isBlank() ? "REQ" : familia, k -> new ArrayList<>())
					.add(r);
		}

		StringBuilder m = new StringBuilder();
		m.append("graph TD\n");

		int i = 0;
		for (Map.Entry<String, List<RequirementInput>> e : porFamilia.entrySet()) {
			String idFamilia = "F" + (i++);
			m.append("  ").append(idFamilia).append("{{\"").append(escapar(e.getKey()))
					.append(" — ").append(e.getValue().size()).append(" requisitos\"}}\n");

			for (RequirementInput r : e.getValue()) {
				String id = "R" + identificador(r.readableId());
				String forma = r.tieneCriterio() ? "[\"" : "(\"";
				String cierre = r.tieneCriterio() ? "\"]" : "\")";

				m.append("  ").append(id).append(forma).append(escapar(r.etiqueta()))
						.append("<br/>").append(escapar(recortar(nombreDe(r), 34))).append(cierre)
						.append('\n');
				m.append("  ").append(idFamilia).append(" --> ").append(id).append('\n');
			}
		}

		long sinCriterio = requisitos.stream().filter(r -> !r.tieneCriterio()).count();

		return new ArtifactProposal(TRAZABILIDAD,
				"Mapa de requisitos por familia",
				m.toString(),
				"MERMAID",
				"Agrupa los requisitos por el prefijo de su identificador de origen. Los que tienen "
						+ "criterio de verificacion se dibujan con esquina recta y los que no, "
						+ "redondeada"
						+ (sinCriterio > 0 ? ": hay " + sinCriterio + " sin criterio" : ""),
				sinCriterio > 0,
				requisitos.stream().map(RequirementInput::readableId).toList());
	}

	// =================================================================

	private String condicionDe(String enunciado) {
		String plano = normalizar(enunciado);
		for (String marca : List.of("cuando ", "siempre que ", "si ")) {
			int donde = plano.indexOf(marca);
			if (donde >= 0) {
				String resto = enunciado.substring(donde + marca.length());
				int coma = resto.indexOf(',');
				return recortar(coma > 0 ? resto.substring(0, coma) : resto, 50);
			}
		}
		return "[indique la condicion]";
	}

	private String nombreDe(RequirementInput r) {
		return r.name() == null || r.name().isBlank() ? r.statement() : r.name();
	}

	private String recortar(String texto, int maximo) {
		String t = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
		return t.length() <= maximo ? t : t.substring(0, maximo - 1) + "…";
	}

	/**
	 * Escapa lo que Mermaid interpreta.
	 *
	 * <p>Las comillas y los corchetes cierran una caja antes de tiempo y el
	 * diagrama deja de dibujarse entero: un enunciado con un corchete bastaria
	 * para que la pantalla apareciese en blanco sin decir por que.</p>
	 */
	private String escapar(String texto) {
		return texto == null ? ""
				: texto.replace("\"", "'").replace("[", "(").replace("]", ")")
						.replace("{", "(").replace("}", ")").replace("|", "/");
	}

	/** Identificador apto para Mermaid: solo letras y digitos. */
	private String identificador(String readableId) {
		return readableId.replaceAll("[^A-Za-z0-9]", "");
	}

	private static String normalizar(String texto) {
		String base = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(base, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
	}
}
