package org.slcp.service.requirements;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementStatus;
import org.slcp.service.generation.GeneratedArtifactRepository;
import org.slcp.service.ingestion.RequirementLinter;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informe de los requisitos de un proyecto, con todo lo que consta de ellos.
 *
 * <p>Reune lo que en la pantalla esta repartido: el texto, los hallazgos del
 * revisor, la procedencia de cada campo, quien decidio y cuando, y si tiene
 * pruebas aceptadas. Repartido se consulta bien de uno en uno; junto se ve lo
 * que solo aparece al mirar el conjunto --- cuantos carecen de criterio, cuantos
 * no tiene ninguna prueba, en cuantos escribio el texto un modelo.</p>
 *
 * <p>No se calcula nada nuevo aqui: se recoge. Un informe que dedujera cosas por
 * su cuenta acabaria discrepando de las pantallas de las que sale.</p>
 */
@Service
public class RequirementReportService {

	/** Una decision tomada sobre una version del requisito. */
	public record DecisionLine(
			int version, String decision, String actor, Instant at, String statement) {
	}

	/** Un requisito con todo lo que consta de el. */
	public record ReportRow(
			String readableId,
			String sourceId,
			String kind,
			String kindLabel,
			String name,
			String statement,
			/** El criterio de verificacion. Nulo si no lo tiene. */
			String verification,
			/** Interesado o fuente. No es el actor de un caso de uso. */
			String actor,
			String status,
			int version,
			/** Si el enunciado lo escribio una persona o lo propuso un modelo. */
			String statementOrigin,
			String verificationOrigin,
			boolean conforming,
			List<String> findings,
			/** Pruebas generadas y aceptadas sobre este requisito. */
			long tests,
			long acceptedTests,
			boolean covered,
			List<DecisionLine> decisions,
			Instant updatedAt) {
	}

	/**
	 * Lo que el conjunto revela y una ficha suelta no.
	 *
	 * <p>Estas cuentas son el motivo del informe: sin ellas hay que abrir veinte
	 * requisitos para saber cuantos carecen de criterio.</p>
	 */
	public record ReportSummary(
			int total,
			int approved,
			int withoutCriterion,
			int withFindings,
			int withoutTests,
			int suggestedText,
			int withoutActor) {
	}

	public record RequirementReport(
			String projectId,
			String projectName,
			Instant generatedAt,
			/**
			 * Si quien consulta puede llevarse el informe.
			 *
			 * <p>Lo puede el equipo y el facilitador; el propietario del producto, no.
			 * Este dato es para que la pantalla no ofrezca un boton que fallaria: la
			 * restriccion se impone al exportar, no aqui.</p>
			 */
			boolean canExport,
			ReportSummary summary,
			List<ReportRow> rows) {
	}

	private final ResourceLoader resources;
	private final RequirementRepository requirements;
	private final RequirementDecisionRepository decisions;
	private final GeneratedArtifactRepository artifacts;
	private final ProjectService projects;
	private final Clock clock;

	public RequirementReportService(RequirementRepository requirements,
			RequirementDecisionRepository decisions, GeneratedArtifactRepository artifacts,
			ProjectService projects, ResourceLoader resources, Clock clock) {

		this.resources = resources;
		this.requirements = requirements;
		this.decisions = decisions;
		this.artifacts = artifacts;
		this.projects = projects;
		this.clock = clock;
	}

	/**
	 * Compone el informe.
	 *
	 * <p>Lo puede pedir cualquiera con acceso al proyecto: es lectura de lo que ya
	 * consta, y restringirlo obligaria a pedirselo a otro para saber en que estado
	 * esta el trabajo propio.</p>
	 */
	@Transactional(readOnly = true)
	public RequirementReport componer(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);

		Map<UUID, Object[]> cobertura = new HashMap<>();
		artifacts.cobertura(proyecto.getId()).forEach(f -> cobertura.put((UUID) f[0], f));

		RequirementLinter revisor = cargarRevisor();

		List<Requirement> todos = requirements
				.findByProjectIdOrderByReadableIdAsc(proyecto.getId());

		List<ReportRow> filas = new ArrayList<>();
		int sinCriterio = 0;
		int conHallazgos = 0;
		int sinPruebas = 0;
		int textoSugerido = 0;
		int sinInteresado = 0;
		int aprobados = 0;

		for (Requirement r : todos) {
			List<String> hallazgos = hallazgosDe(revisor, r);
			Object[] c = cobertura.get(r.getId());

			long pruebas = c == null ? 0 : ((Number) c[1]).longValue();
			long aceptadas = c == null ? 0 : ((Number) c[2]).longValue();

			boolean tieneCriterio = r.getVerification() != null && !r.getVerification().isBlank();
			if (!tieneCriterio) {
				sinCriterio++;
			}
			if (!hallazgos.isEmpty()) {
				conHallazgos++;
			}
			if (pruebas == 0) {
				sinPruebas++;
			}
			if (r.getStatementOrigin() != null && "SUGGESTED".equals(r.getStatementOrigin().name())) {
				textoSugerido++;
			}
			if (r.getActor() == null || r.getActor().isBlank()) {
				sinInteresado++;
			}
			if (r.getStatus() == RequirementStatus.APPROVED) {
				aprobados++;
			}

			// La etiqueta del estado no viaja: la pone la interfaz, que ya tiene su
			// tabla. Traducirla aqui daria dos traducciones que pueden discrepar.
			filas.add(new ReportRow(r.getReadableId(), r.getSourceId(), r.getKind().name(),
					r.getKind().getEtiqueta(), r.getName(), r.getStatement(), r.getVerification(),
					r.getActor(), r.getStatus().name(), r.getVersion(),
					r.getStatementOrigin() == null ? null : r.getStatementOrigin().name(),
					r.getVerificationOrigin() == null ? null : r.getVerificationOrigin().name(),
					hallazgos.isEmpty(), hallazgos, pruebas, aceptadas,
					c != null && Boolean.TRUE.equals(c[3]),
					decisionesDe(r.getId()), r.getUpdatedAt()));
		}

		return new RequirementReport(proyecto.getReadableId(), proyecto.getName(),
				Instant.now(clock), puedeExportar(proyecto.getId(), solicitante),
				new ReportSummary(todos.size(), aprobados, sinCriterio, conHallazgos, sinPruebas,
						textoSugerido, sinInteresado),
				filas);
	}

	/**
	 * Exporta el informe como valores separados por comas.
	 *
	 * <p>Se genera aqui y no en el navegador porque hay una restriccion que
	 * cumplir: si el archivo se armara con datos ya enviados, quien no puede
	 * exportar los tendria igualmente y le bastaria con copiarlos. Una restriccion
	 * que se puede rodear no es una restriccion.</p>
	 */
	@Transactional(readOnly = true)
	public String exportar(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);

		if (!puedeExportar(proyecto.getId(), solicitante)) {
			throw new ProjectAccessException(
					"Llevarse el informe corresponde al equipo de desarrollo y al facilitador. El "
							+ "propietario del producto puede consultarlo en pantalla");
		}

		RequirementReport informe = componer(projectReadableId, solicitante);
		StringBuilder csv = new StringBuilder();

		csv.append(fila(List.of("Identificador", "Origen", "Tipo", "Nombre", "Enunciado",
				"Criterio de verificacion", "Interesado o fuente", "Estado", "Version",
				"Procedencia del enunciado", "Procedencia del criterio", "Conforme", "Hallazgos",
				"Pruebas", "Pruebas aceptadas", "Cubierto")));

		for (ReportRow f : informe.rows()) {
			csv.append(fila(List.of(
					texto(f.readableId()), texto(f.sourceId()), texto(f.kindLabel()),
					texto(f.name()), texto(f.statement()), texto(f.verification()),
					texto(f.actor()), texto(f.status()), String.valueOf(f.version()),
					procedencia(f.statementOrigin()), procedencia(f.verificationOrigin()),
					f.conforming() ? "Si" : "No", String.join(" | ", f.findings()),
					String.valueOf(f.tests()), String.valueOf(f.acceptedTests()),
					f.covered() ? "Si" : "No")));
		}

		return csv.toString();
	}

	private String fila(List<String> valores) {
		StringBuilder linea = new StringBuilder();
		for (int i = 0; i < valores.size(); i++) {
			if (i > 0) {
				linea.append(',');
			}
			linea.append(escapar(valores.get(i)));
		}
		return linea.append("\r\n").toString();
	}

	/** Escapa segun el RFC 4180: comillas dobladas y campo entrecomillado. */
	private String escapar(String valor) {
		String limpio = valor.replace("\"", "\"\"");
		return limpio.matches("(?s).*[\",\r\n].*") ? "\"" + limpio + "\"" : limpio;
	}

	private String texto(String valor) {
		return valor == null ? "" : valor;
	}

	private String procedencia(String origen) {
		return "SUGGESTED".equals(origen) ? "Propuesta por el sistema" : "Escrita por una persona";
	}

	/**
	 * Quien puede llevarse el informe.
	 *
	 * <p>El equipo y el facilitador, que son quienes trabajan sobre el. El
	 * propietario del producto lo consulta en pantalla: decide sobre los requisitos
	 * y los entregables, y para eso no necesita el detalle interno --- hallazgos
	 * del revisor, procedencia de cada campo --- fuera de la plataforma.</p>
	 */
	private boolean puedeExportar(UUID projectId, UUID solicitante) {
		List<ProjectRole> roles = projects.rolesEn(projectId, solicitante);

		return roles.contains(ProjectRole.TEAM_MEMBER)
				|| roles.contains(ProjectRole.PROJECT_FACILITATOR);
	}

	// =================================================================

	/**
	 * Hallazgos del revisor sobre el enunciado.
	 *
	 * <p>Se recalculan al componer el informe y no se guardan: las reglas pueden
	 * haber cambiado desde que el requisito se dio de alta, y un hallazgo guardado
	 * diria lo que se penso entonces en lugar de lo que se piensa ahora.</p>
	 */
	private List<String> hallazgosDe(RequirementLinter linter, Requirement r) {
		if (linter == null) {
			return List.of();
		}
		return linter.examinar(r.getStatement()).stream()
				.map(h -> h.regla() + " (" + h.gravedad() + "): " + h.explicacion())
				.toList();
	}

	/**
	 * Carga el revisor de enunciados.
	 *
	 * <p>Si no puede cargarse, el informe sale igual y sin hallazgos: que falten las
	 * reglas no debe dejar sin informe, porque lo demas sigue valiendo.</p>
	 */
	private RequirementLinter cargarRevisor() {
		try (Reader r = new InputStreamReader(
				resources.getResource("classpath:rules/es-29148.rules").getInputStream(),
				StandardCharsets.UTF_8)) {

			return RequirementLinter.cargar(r);

		} catch (IOException e) {
			return null;
		}
	}

	private List<DecisionLine> decisionesDe(UUID requirementId) {
		return decisions.findByRequirementIdOrderByVersionAscDecidedAtAsc(requirementId).stream()
				.map(d -> new DecisionLine(d.getVersion(), d.getDecision(), d.getActorLabel(),
						d.getDecidedAt(), d.getStatement()))
				.toList();
	}

}
