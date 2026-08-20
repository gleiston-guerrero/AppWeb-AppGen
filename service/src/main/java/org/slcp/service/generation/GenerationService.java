package org.slcp.service.generation;

import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.GeneratedArtifact;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementStatus;
import org.slcp.service.domain.User;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.slcp.service.requirements.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera pruebas y diagramas a partir de los requisitos aprobados.
 *
 * <p>En cualquier orden y de forma independiente: las pruebas no necesitan los
 * diagramas ni al reves. Quien quiera solo diagramas no tiene que generar
 * pruebas antes.</p>
 *
 * <p>Lo generado nace propuesto. Aceptarlo es un acto de una persona, y de ahi
 * sale la cobertura: un requisito esta cubierto cuando alguien acepto una prueba
 * suya, no cuando la plataforma la escribio.</p>
 */
@Service
public class GenerationService {

	/** Peticion de generacion. */
	public record GenerateRequest(
			@NotBlank String kind,
			List<String> subkinds,
			/** Requisitos concretos, o vacio para todos los aprobados. */
			List<String> requirements,

			/**
			 * Como se genera: derivado del texto o asistido por un modelo.
			 *
			 * <p>Lo elige quien genera y no la plataforma. Lo derivado es
			 * reproducible y no sale del proyecto; lo asistido identifica lo que el
			 * enunciado no dice --- un actor de dominio, un escenario alternativo ---
			 * a cambio de enviar el requisito a un tercero y de que dos generaciones
			 * puedan no coincidir. Ninguna de las dos es siempre la buena.</p>
			 */
			String mode) {

		public static final String DERIVADO = "DERIVED";
		public static final String ASISTIDO = "ASSISTED";

		public boolean pideAsistencia() {
			return ASISTIDO.equals(mode);
		}
	}

	/** Artefacto tal como lo ve quien consulta. */
	public record ArtifactView(
			String readableId,
			String kind,
			String subkind,
			String subkindLabel,
			String title,
			String content,
			String format,
			String origin,
			String originLabel,
			String rationale,
			boolean needsDecision,
			String status,
			int version,
			String reviewedBy,
			/** Se acepto teniendo huecos: la decision del equipo prevalecio. */
			boolean acceptedWithGaps,
			/** Propietario que lo dio por revisado. No es aprobacion. */
			String ownerReviewedBy,
			Instant ownerReviewedAt,
			List<String> requirements,
			Instant updatedAt) {
	}

	/** Cobertura de un requisito aprobado. */
	public record CoverageView(
			String readableId, String sourceId, String name, long tests, long acceptedTests,
			boolean covered) {
	}

	/** Lo que ofrece la pantalla: que puede generarse y que hay generado. */
	public record GenerationView(
			List<String> testKinds,
			List<String> diagramKinds,
			boolean assisted,
			List<ArtifactView> tests,
			List<ArtifactView> diagrams,
			List<CoverageView> coverage) {
	}

	private final GeneratedArtifactRepository artifacts;
	private final RequirementRepository requirements;
	private final SpecificationRepository specifications;
	private final TestGenerator tests;
	private final AiSettingsService ia;
	private final DiagramGenerator diagrams;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final Clock clock;

	public GenerationService(GeneratedArtifactRepository artifacts,
			RequirementRepository requirements, SpecificationRepository specifications,
			TestGenerator tests, DiagramGenerator diagrams,
			AiSettingsService ia, ProjectService projects, UserRepository users,
			EventRecordRepository events, Clock clock) {

		this.artifacts = artifacts;
		this.requirements = requirements;
		this.specifications = specifications;
		this.tests = tests;
		this.ia = ia;
		this.diagrams = diagrams;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	// =================================================================

	@Transactional(readOnly = true)
	public GenerationView consultar(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);

		return new GenerationView(tests.clases(), diagrams.clases(),
				ia.generadorDe(proyecto.getId(), tests).isPresent(),
				vistas(proyecto.getId(), "TEST"),
				vistas(proyecto.getId(), "DIAGRAM"),
				cobertura(proyecto.getId()));
	}

	/**
	 * Genera pruebas o diagramas.
	 *
	 * <p>Corresponde a quien produce. Lo generado no altera nada de lo existente:
	 * se anade, y lo anterior sigue donde estaba. Regenerar dos veces produce dos
	 * propuestas, y esa duplicidad es preferible a sobrescribir en silencio algo
	 * que alguien ya habia revisado.</p>
	 */
	@Transactional
	public List<ArtifactView> generar(String projectReadableId, GenerateRequest peticion,
			UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Instant momento = Instant.now(clock);

		List<Requirement> aprobados = requirements
				.findByProjectIdAndStatusOrderByReadableIdAsc(proyecto.getId(),
						RequirementStatus.APPROVED);

		if (!peticion.requirements().isEmpty()) {
			Set<String> pedidos = new HashSet<>(peticion.requirements());
			aprobados = aprobados.stream()
					.filter(r -> pedidos.contains(r.getReadableId())).toList();
		}

		if (aprobados.isEmpty()) {
			throw new GenerationException(
					"No hay requisitos aprobados que generar. Solo se generan pruebas y diagramas "
							+ "de requisitos aprobados: hacerlo sobre uno que aun puede cambiar "
							+ "produciria artefactos que habria que rehacer");
		}

		List<RequirementInput> entrada = aprobados.stream().map(this::entradaDe).toList();
		List<ArtifactProposal> propuestas = new ArrayList<>();

		// El generador se resuelve por proyecto y en cada uso: la configuracion
		// cambia sin reiniciar, y uno construido al arranque seguiria usando la
		// credencial anterior.
		// Solo se pide asistencia si quien genera la eligio. Que este configurada no
		// significa que se quiera para todo: lo derivado es reproducible y no sale
		// del proyecto, y para muchas pruebas basta.
		TestGenerator generadorDePruebas = peticion.pideAsistencia()
				? ia.generadorDe(proyecto.getId(), tests).orElseThrow(() -> new GenerationException(
						"Se pidio generacion asistida y este proyecto no tiene un servicio de IA "
								+ "activo. Configurelo, o genere sin asistencia"))
				: tests;

		boolean esPrueba = "TEST".equals(peticion.kind());
		List<String> clases = peticion.subkinds() == null || peticion.subkinds().isEmpty()
				? (esPrueba ? tests.clases() : diagrams.clases())
				: peticion.subkinds();

		for (String clase : clases) {
			if (esPrueba) {
				for (RequirementInput r : entrada) {
					propuestas.addAll(generadorDePruebas.generar(r, clase));
				}
			} else {
				propuestas.addAll(diagrams.generar(entrada, clase));
			}
		}

		Map<String, UUID> porIdentificador = new HashMap<>();
		aprobados.forEach(r -> porIdentificador.put(r.getReadableId(), r.getId()));

		String origen = esPrueba && generadorDePruebas instanceof AssistedTestGenerator
				? GeneratedArtifact.ASISTIDO
				: GeneratedArtifact.DERIVADO;

		int secuencia = artifacts.mayorNumero(proyecto.getId(), peticion.kind());
		List<ArtifactView> salida = new ArrayList<>();

		for (ArtifactProposal p : propuestas) {
			secuencia++;
			String readableId = String.format("%s-%04d", esPrueba ? "PRB" : "DIA", secuencia);

			GeneratedArtifact artefacto = GeneratedArtifact.crear(proyecto.getId(), readableId,
					peticion.kind(), p.subkind(), p.title(), p.content(), p.format(),
					// La procedencia la marca cada propuesta: el generador asistido
					// recurre al derivado cuando el servicio externo falla, y decir
					// que todo es asistido seria falso justo cuando mas importa.
					p.rationale().startsWith("Redactada por el modelo") ? GeneratedArtifact.ASISTIDO
							: (esPrueba ? origen : GeneratedArtifact.DERIVADO),
					p.rationale(), p.needsDecision(), autor, momento);

			artifacts.save(artefacto);

			for (String requisito : p.requirements()) {
				UUID id = porIdentificador.get(requisito);
				if (id != null) {
					artifacts.enlazar(artefacto.getId(), id);
				}
			}

			salida.add(vistaDe(artefacto));
		}

		registrar(esPrueba ? "TESTS_GENERATED" : "DIAGRAMS_GENERATED", proyecto.getId(), autor,
				salida.size() + " de " + aprobados.size() + " requisitos", momento);

		return salida;
	}

	@Transactional
	public ArtifactView editar(String projectReadableId, String readableId, String title,
			String content, UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		GeneratedArtifact artefacto = buscar(proyecto.getId(), readableId);

		artefacto.editar(title, content, Instant.now(clock));
		registrar("ARTIFACT_EDITED", proyecto.getId(), autor, readableId, Instant.now(clock));

		return vistaDe(artefacto);
	}

	/**
	 * Acepta o descarta un artefacto.
	 *
	 * <p>Corresponde a quien produce, no al propietario del producto: son
	 * artefactos tecnicos y quien los juzga es quien va a ejecutarlos. Lo que el
	 * propietario aprueba son los requisitos y los entregables.</p>
	 */
	@Transactional
	public ArtifactView decidir(String projectReadableId, String readableId, boolean aceptar,
			UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		GeneratedArtifact artefacto = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		if (aceptar) {
			artefacto.aceptar(autor, momento);
		} else {
			artefacto.descartar(autor, momento);
		}

		registrar(aceptar ? "ARTIFACT_ACCEPTED" : "ARTIFACT_DISCARDED", proyecto.getId(), autor,
				readableId, momento);

		return vistaDe(artefacto);
	}

	/**
	 * El propietario del producto da por revisado lo generado.
	 *
	 * <p>Puede hacerlo antes o despues de que el equipo lo apruebe, y no altera
	 * esa aprobacion. Lo que el propietario aprueba son los requisitos y los
	 * entregables; aqui solo deja constancia de haberlo visto.</p>
	 */
	@Transactional
	public ArtifactView darPorRevisado(String projectReadableId, String readableId,
			boolean revisado, UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.PRODUCT_OWNER);
		GeneratedArtifact artefacto = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		artefacto.darPorRevisado(revisado, autor, momento);
		registrar(revisado ? "ARTIFACT_OWNER_REVIEWED" : "ARTIFACT_OWNER_REVIEW_WITHDRAWN",
				proyecto.getId(), autor, readableId, momento);

		return vistaDe(artefacto);
	}

	@Transactional
	public void eliminar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		GeneratedArtifact artefacto = buscar(proyecto.getId(), readableId);

		if (GeneratedArtifact.ACEPTADO.equals(artefacto.getStatus())) {
			throw new GenerationException(
					"Este artefacto fue aceptado y no puede eliminarse: su aceptacion cuenta como "
							+ "cobertura del requisito. Descartelo si deja de valer, que conserva "
							+ "su historia");
		}

		registrar("ARTIFACT_DELETED", proyecto.getId(), autor, readableId, Instant.now(clock));
		artifacts.delete(artefacto);
	}

	// =================================================================

	/**
	 * Lo que se le da al generador.
	 *
	 * <p>Incluye el caso de uso aceptado si lo hay. Es lo que el equipo decidio, y
	 * de ahi salen los caminos negativos y los actores que el requisito no trae:
	 * generar del requisito habiendo un caso de uso aceptado seria ignorar el
	 * trabajo de quien lo escribio y volver a inventar lo ya decidido.</p>
	 */
	private RequirementInput entradaDe(Requirement r) {
		return new RequirementInput(r.getReadableId(), r.getSourceId(), r.getKind().name(),
				r.getName(), r.getStatement(), r.getVerification(), r.getActor(),
				casoDeUsoDe(r.getId()));
	}

	/** El caso de uso aceptado que realiza este requisito, si existe. */
	private String casoDeUsoDe(UUID requirementId) {
		return specifications.casoDeUsoAceptadoDe(requirementId).orElse(null);
	}

	private List<ArtifactView> vistas(UUID projectId, String kind) {
		return artifacts.findByProjectIdAndKindOrderByReadableIdAsc(projectId, kind).stream()
				.map(this::vistaDe).toList();
	}

	private ArtifactView vistaDe(GeneratedArtifact a) {
		List<String> requisitos = artifacts.requisitosDe(a.getId()).stream()
				.map(requirements::findById)
				.filter(java.util.Optional::isPresent)
				.map(o -> {
					Requirement r = o.get();
					return r.getSourceId() == null || r.getSourceId().isBlank()
							? r.getReadableId() : r.getSourceId();
				})
				.toList();

		String revisor = a.getReviewedBy() == null ? null
				: users.findById(a.getReviewedBy()).map(User::getUsername).orElse(null);

		String propietario = a.getOwnerReviewedBy() == null ? null
				: users.findById(a.getOwnerReviewedBy()).map(User::getUsername).orElse(null);

		return new ArtifactView(a.getReadableId(), a.getKind(), a.getSubkind(),
				etiquetaDe(a.getSubkind()), a.getTitle(), a.getContent(), a.getFormat(),
				a.getOrigin(), etiquetaOrigen(a.getOrigin()), a.getRationale(),
				a.isNeedsDecision(), a.getStatus(), a.getVersion(), revisor, a.isAcceptedWithGaps(),
				propietario,
				a.getOwnerReviewedAt(), requisitos, a.getUpdatedAt());
	}

	private List<CoverageView> cobertura(UUID projectId) {
		Map<UUID, Object[]> filas = new HashMap<>();
		artifacts.cobertura(projectId).forEach(f -> filas.put((UUID) f[0], f));

		return requirements
				.findByProjectIdAndStatusOrderByReadableIdAsc(projectId, RequirementStatus.APPROVED)
				.stream()
				.map(r -> {
					Object[] f = filas.get(r.getId());
					return new CoverageView(r.getReadableId(), r.getSourceId(), r.getName(),
							f == null ? 0 : ((Number) f[1]).longValue(),
							f == null ? 0 : ((Number) f[2]).longValue(),
							f != null && Boolean.TRUE.equals(f[3]));
				})
				.toList();
	}

	private String etiquetaDe(String subkind) {
		return switch (subkind) {
			case "ACCEPTANCE" -> "Aceptación";
			case "BOUNDARY" -> "Límites";
			case "NEGATIVE" -> "Camino negativo";
			case "PERFORMANCE" -> "Rendimiento";
			case "USE_CASE" -> "Casos de uso";
			case "STATE" -> "Estados";
			case "CONTEXT" -> "Contexto";
			case "TRACEABILITY" -> "Trazabilidad";
			default -> subkind;
		};
	}

	private String etiquetaOrigen(String origin) {
		return switch (origin) {
			case GeneratedArtifact.DERIVADO -> "Derivado del requisito";
			case GeneratedArtifact.ASISTIDO -> "Asistido por modelo";
			default -> "Escrito por una persona";
		};
	}

	private GeneratedArtifact buscar(UUID projectId, String readableId) {
		return artifacts.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new GenerationException("No existe ese artefacto en el proyecto"));
	}

	private Project exigirRol(String readableId, UUID solicitante, ProjectRole rol) {
		Project proyecto = projects.exigirAccesoPublico(readableId, solicitante);

		if (!projects.rolesEn(proyecto.getId(), solicitante).contains(rol)) {
			throw new ProjectAccessException(
					"Esta operacion corresponde al " + rol.getEtiqueta().toLowerCase());
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle,
			Instant momento) {

		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
