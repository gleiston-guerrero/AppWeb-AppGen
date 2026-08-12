package org.slcp.service.requirements;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementKind;
import org.slcp.service.domain.RequirementStatus;
import org.slcp.service.domain.TextOrigin;
import org.slcp.service.domain.User;
import org.slcp.service.ingestion.CriterionSuggester;
import org.slcp.service.ingestion.ExtractionReport;
import org.slcp.service.ingestion.ImportProfile;
import org.slcp.service.ingestion.ParsedRequirement;
import org.slcp.service.ingestion.RequirementSource;
import org.slcp.service.ingestion.RequirementLinter;
import org.slcp.service.ingestion.StatementSuggester;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.slcp.service.requirements.RequirementContracts.FindingView;
import org.slcp.service.requirements.RequirementContracts.ImportRequest;
import org.slcp.service.requirements.RequirementContracts.ImportResult;
import org.slcp.service.requirements.RequirementContracts.RequirementRequest;
import org.slcp.service.requirements.RequirementContracts.RequirementSummary;
import org.slcp.service.requirements.RequirementContracts.RequirementView;
import org.slcp.service.requirements.RequirementContracts.SuggestionView;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requisitos de un proyecto: alta, importacion, validacion y sugerencia.
 *
 * <p>La validacion no se almacena. Los hallazgos y las propuestas se calculan al
 * consultar, de modo que un cambio en las reglas se refleja de inmediato sin
 * alterar el estado de ningun requisito, conforme a Q-65. Guardarlos los dejaria
 * obsoletos en silencio, que es el modo de fallo que este proyecto persigue
 * desde el principio.</p>
 */
@Service
public class RequirementService {

	private final RequirementRepository requirements;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final CriterionSuggester suggester;
	private final ResourceLoader resources;
	private final Clock clock;

	public RequirementService(RequirementRepository requirements, ProjectService projects,
			UserRepository users, EventRecordRepository events, CriterionSuggester suggester,
			ResourceLoader resources, Clock clock) {
		this.requirements = requirements;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.suggester = suggester;
		this.resources = resources;
		this.clock = clock;
	}

	// =================================================================
	// Consulta
	// =================================================================

	@Transactional(readOnly = true)
	public List<RequirementView> listar(String projectReadableId, UUID solicitante) {
		Project proyecto = exigirMembresia(projectReadableId, solicitante);
		RequirementLinter linter = cargarLinter();

		return requirements.findByProjectIdOrderByReadableIdAsc(proyecto.getId()).stream()
				.map(r -> vista(r, linter))
				.toList();
	}

	@Transactional(readOnly = true)
	public RequirementSummary resumen(String projectReadableId, UUID solicitante) {
		List<RequirementView> vistas = listar(projectReadableId, solicitante);

		return new RequirementSummary(
				vistas.size(),
				vistas.stream().filter(RequirementView::conforming).count(),
				vistas.stream().filter(v -> !v.findings().isEmpty()).count(),
				vistas.stream().filter(v -> v.verification() == null || v.verification().isBlank()).count(),
				vistas.stream().filter(v -> "APPROVED".equals(v.status())).count(),
				vistas.stream().filter(v -> "SUGGESTED".equals(v.statementOrigin())
						|| "SUGGESTED".equals(v.verificationOrigin())).count());
	}

	// =================================================================
	// Alta y edicion
	// =================================================================

	/** Crea un requisito escrito a mano. Exige ser miembro del equipo (ROL-02). */
	@Transactional
	public RequirementView crear(String projectReadableId, RequirementRequest peticion, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Instant momento = Instant.now(clock);

		Requirement requisito = Requirement.crear(proyecto.getId(),
				siguienteIdentificador(proyecto.getId()),
				peticion.sourceId(), null,
				tipoDe(peticion.kind(), peticion.sourceId()),
				peticion.name(), peticion.statement(), peticion.verification(),
				autor, momento);

		requirements.save(requisito);
		registrar("REQUIREMENT_CREATED", proyecto.getId(), autor, requisito.getReadableId(), momento);

		return vista(requisito, cargarLinter());
	}

	/**
	 * Modifica un requisito.
	 *
	 * @param aceptaSugerencia si el texto procede de una propuesta de la
	 *                         plataforma, para poder marcarlo conforme a ANA-16
	 */
	@Transactional
	public RequirementView editar(String projectReadableId, String readableId,
			RequirementRequest peticion, boolean aceptaSugerencia, UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Requirement requisito = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		TextOrigin origen = aceptaSugerencia ? TextOrigin.SUGGESTED : TextOrigin.HUMAN;
		requisito.editar(peticion.name(), peticion.statement(), peticion.verification(),
				origen, origen, momento);

		registrar(aceptaSugerencia ? "REQUIREMENT_SUGGESTION_ACCEPTED" : "REQUIREMENT_EDITED",
				proyecto.getId(), autor, requisito.getReadableId(), momento);

		return vista(requisito, cargarLinter());
	}

	/**
	 * Cambia el estado.
	 *
	 * <p>La aprobacion de un requisito ocurre en dos etapas y las hacen roles
	 * distintos: el facilitador revisa y el propietario del producto aprueba
	 * (RQM-05). Concentrar ambas en una persona dejaria la aprobacion sin
	 * contraste, que es justamente lo que la doble etapa evita.</p>
	 */
	@Transactional
	public RequirementView transitar(String projectReadableId, String readableId,
			String destino, UUID autor) {

		RequirementStatus estado = RequirementStatus.valueOf(destino);
		ProjectRole exigido = rolQueDecide(estado);

		Project proyecto = exigirRol(projectReadableId, autor, exigido);
		Requirement requisito = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		// RQM-05: quien reviso no puede ser quien aprueba. Se comprueba sobre la
		// misma persona y el mismo requisito, no sobre el rol: en un equipo pequeno
		// una persona puede tener ambos roles, y precisamente por eso hace falta
		// impedir que recorra sola las dos etapas.
		if (estado == RequirementStatus.APPROVED
				&& autor.equals(requisito.getReviewedBy())) {
			throw new RequirementException(
					"Usted reviso este requisito, de modo que no puede aprobarlo. La revision y la "
							+ "aprobacion son dos etapas y las hacen dos personas distintas");
		}

		if (estado == RequirementStatus.REVIEWED) {
			requisito.registrarRevision(autor);
		}

		requisito.transitarA(estado, momento);
		registrar("REQUIREMENT_" + estado.name(), proyecto.getId(), autor,
				requisito.getReadableId(), momento);

		return vista(requisito, cargarLinter());
	}

	/**
	 * Elimina un requisito.
	 *
	 * <p>Solo mientras nada se haya decidido sobre el. Uno revisado o aprobado se
	 * anula, no se borra: la anulacion lo retira de lo exigible y conserva su
	 * historia, y borrarlo destruiria la constancia de quien decidio que.</p>
	 */
	@Transactional
	public void eliminar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Requirement requisito = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		if (!requisito.puedeEliminarse()) {
			throw new RequirementException(
					"Este requisito fue revisado o aprobado y no puede eliminarse. Anulelo si deja "
							+ "de exigirse: la anulacion lo retira conservando su historia");
		}

		registrar("REQUIREMENT_DELETED", proyecto.getId(), autor, requisito.getReadableId(), momento);
		requirements.delete(requisito);
	}

	// =================================================================
	// Importacion
	// =================================================================

	/**
	 * Carga un documento completo.
	 *
	 * <p>Lo importado nace en borrador y con sus carencias reportadas. Nada se
	 * completa ni se corrige al importar: TRC-13 distingue lo que el documento
	 * dice de lo que alguien supone que quiso decir.</p>
	 */
	@Transactional
	public ImportResult importar(String projectReadableId, ImportRequest peticion, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Instant momento = Instant.now(clock);

		ExtractionReport informe;
		try {
			ImportProfile perfil = cargarPerfil(peticion.profileId());
			// El lector lo elige el perfil: un JSON se analiza como JSON y un CSV por
			// columnas. Forzar el de lineas sobre todos funcionaria con los archivos
			// bien formateados y fallaria con los compactos.
			informe = RequirementSource.of(perfil).extraer(new StringReader(peticion.content()));
		} catch (IOException e) {
			throw new RequirementException("No se pudo leer el documento: " + e.getMessage());
		}

		long secuencia = requirements.countByProjectId(proyecto.getId());
		List<String> omitidos = new ArrayList<>();
		int importados = 0;

		for (ParsedRequirement parsed : informe.requirements()) {
			String sourceId = parsed.sourceId();

			// Un identificador ya presente no se sobreescribe: importar dos veces el
			// mismo documento no debe duplicar ni pisar el trabajo hecho encima.
			if (sourceId != null && !sourceId.isBlank()
					&& requirements.findByProjectIdAndSourceId(proyecto.getId(), sourceId).isPresent()) {
				omitidos.add(sourceId);
				continue;
			}

			secuencia++;
			Requirement requisito = Requirement.crear(proyecto.getId(),
					String.format("REQ-%04d-v1", secuencia),
					sourceId, parsed.sourceLine(),
					RequirementKind.conjeturar(sourceId),
					parsed.get("name"),
					enunciadoDe(parsed),
					parsed.get("verification"),
					autor, momento);

			requirements.save(requisito);
			importados++;
		}

		registrar("REQUIREMENTS_IMPORTED", proyecto.getId(), autor,
				importados + " de " + informe.total(), momento);

		String mensaje = importados + " requisitos importados de " + informe.total() + " encontrados."
				+ (omitidos.isEmpty() ? "" : " " + omitidos.size()
						+ " se omitieron por existir ya en el proyecto.")
				+ " Todos quedan en borrador y con sus carencias reportadas.";

		return new ImportResult(informe.total(), importados, omitidos.size(), omitidos,
				informe.missingByField(), informe.unknownLabels(), mensaje);
	}

	// =================================================================
	// Interno
	// =================================================================

	/**
	 * Enunciado del requisito.
	 *
	 * <p>Los tipos que no traen descripcion la tienen en otro campo: las historias
	 * de usuario en su enunciado, los casos de uso en su flujo principal. Tomar
	 * solo la descripcion dejaria sin enunciado a mas de un tercio del documento.</p>
	 */
	private String enunciadoDe(ParsedRequirement parsed) {
		for (String campo : List.of("description", "statement", "mainFlow", "name")) {
			if (parsed.tiene(campo)) {
				return parsed.get(campo);
			}
		}
		return "";
	}

	private RequirementView vista(Requirement r, RequirementLinter linter) {
		List<FindingView> hallazgos = linter.examinar(r.getStatement()).stream()
				.map(h -> new FindingView(h.regla(), h.caracteristica().getEtiqueta(),
						h.gravedad().name(), h.evidencia(), h.explicacion()))
				.toList();

		// Redacciones alternativas del enunciado, solo si tiene algo que corregir.
		List<SuggestionView> redacciones = new StatementSuggester(linter)
				.proponer(r.getStatement()).stream()
				.map(p -> new SuggestionView(p.texto(), p.fundamento(), p.exigeDecision()))
				.toList();

		List<SuggestionView> propuestas = List.of();
		if (!r.tieneCriterio() && r.getKind().exigeCriterio()) {
			boolean magnitud = r.getKind() == RequirementKind.NON_FUNCTIONAL;
			propuestas = suggester.proponer(r.getStatement(), magnitud).stream()
					.map(p -> new SuggestionView(p.texto(), p.fundamento(), p.exigeDecision()))
					.toList();
		}

		boolean conforme = hallazgos.stream().noneMatch(h -> "DEFECTO".equals(h.severity()))
				&& !r.incompleto();

		return new RequirementView(r.getReadableId(), r.getSourceId(), r.getSourceLine(),
				r.getKind().name(), r.getKind().getEtiqueta(), r.getName(), r.getStatement(),
				r.getVerification(), r.getStatus().name(), r.getVersion(),
				r.getReviewedBy() == null ? null : r.getReviewedBy().toString(),
				r.getStatementOrigin().name(), r.getVerificationOrigin().name(),
				conforme, r.puedeEliminarse(), hallazgos, redacciones, propuestas, r.getUpdatedAt());
	}

	private RequirementLinter cargarLinter() {
		try (Reader r = leer("classpath:rules/es-29148.rules")) {
			return RequirementLinter.cargar(r);
		} catch (IOException e) {
			throw new RequirementException("No se pudieron cargar las reglas de validacion");
		}
	}

	private ImportProfile cargarPerfil(String profileId) throws IOException {
		try (Reader r = leer("classpath:profiles/" + profileId + ".profile")) {
			return ImportProfile.cargar(r);
		} catch (IOException e) {
			throw new RequirementException("No existe el perfil de importacion " + profileId);
		}
	}

	private Reader leer(String ubicacion) throws IOException {
		Resource recurso = resources.getResource(ubicacion);
		return new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8);
	}

	private String siguienteIdentificador(UUID projectId) {
		return String.format("REQ-%04d-v1", requirements.countByProjectId(projectId) + 1);
	}

	private RequirementKind tipoDe(String declarado, String sourceId) {
		if (declarado != null && !declarado.isBlank()) {
			try {
				return RequirementKind.valueOf(declarado);
			} catch (IllegalArgumentException e) {
				throw new RequirementException("Tipo de requisito no reconocido: " + declarado);
			}
		}
		return RequirementKind.conjeturar(sourceId);
	}

	/**
	 * Rol que puede llevar un requisito al estado indicado.
	 *
	 * <p>Revisar corresponde al facilitador, aprobar al propietario, y devolver a
	 * borrador o rechazar a quien produce, que es quien ha de corregirlo.</p>
	 */
	private ProjectRole rolQueDecide(RequirementStatus estado) {
		return switch (estado) {
			case REVIEWED -> ProjectRole.PROJECT_FACILITATOR;
			case APPROVED -> ProjectRole.PRODUCT_OWNER;
			default -> ProjectRole.TEAM_MEMBER;
		};
	}

	private Requirement buscar(UUID projectId, String readableId) {
		return requirements.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new RequirementException("No existe ese requisito en el proyecto"));
	}

	private Project exigirMembresia(String readableId, UUID solicitante) {
		return projects.exigirAccesoPublico(readableId, solicitante);
	}

	private Project exigirRol(String readableId, UUID solicitante, ProjectRole rol) {
		Project proyecto = exigirMembresia(readableId, solicitante);
		if (!projects.rolesEn(proyecto.getId(), solicitante).contains(rol)) {
			throw new ProjectAccessException(
					"Esta operacion corresponde al " + rol.getEtiqueta().toLowerCase());
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle, Instant momento) {
		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
