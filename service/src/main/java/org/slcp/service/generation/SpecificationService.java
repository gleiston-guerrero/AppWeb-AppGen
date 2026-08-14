package org.slcp.service.generation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementStatus;
import org.slcp.service.domain.Specification;
import org.slcp.service.domain.User;
import org.slcp.service.ingestion.JsonParser;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.slcp.service.requirements.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso expandidos e historias de usuario.
 *
 * <p>Se generan con un modelo, se editan, se escriben desde cero, y lo que el
 * equipo acepta pasa a ser regla base: se conserva aunque se vuelva a generar.
 * Regenerar sobre lo aceptado borraria el trabajo de quien lo reviso, y entonces
 * nadie revisaria nada.</p>
 *
 * <p>La excepcion es que cambien los requisitos de los que salio. Por eso se
 * guarda la version que tenian al aceptarse: si la actual es otra, lo aceptado
 * se refiere a un texto que ya no rige, y se marca.</p>
 */
@Service
public class SpecificationService {

	/** Peticion de generacion. */
	public record GenerateRequest(String kind, List<String> requirements) {
	}

	/** Alta o modificacion escrita por una persona. */
	public record SpecificationRequest(
			String kind, String name, String fields, List<String> requirements) {
	}

	/** Un reparo de la comprobacion. */
	public record IssueView(String field, String reason, boolean severe, String source) {
	}

	/** Un caso de uso o una historia, con todo lo que consta. */
	public record SpecificationView(
			String readableId,
			String kind,
			String kindLabel,
			String name,
			/** Los campos, tal como se guardan. */
			String fields,
			String origin,
			String originLabel,
			String status,
			boolean baseline,
			/** Si algun requisito cambio desde que se acepto. */
			boolean outdated,
			int version,
			String acceptedBy,
			int acceptedWithIssues,
			List<String> requirements,
			List<IssueView> issues,
			Instant updatedAt) {
	}

	/** Lo que ofrece la pantalla. */
	public record SpecificationsView(
			/** Si hay modelo activo para esta funcion. Sin el no puede generarse. */
			boolean assisted,
			List<SpecificationView> useCases,
			List<SpecificationView> userStories) {
	}

	private final SpecificationRepository specifications;
	private final RequirementRepository requirements;
	private final AiSettingsService ia;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final Clock clock;

	public SpecificationService(SpecificationRepository specifications,
			RequirementRepository requirements, AiSettingsService ia, ProjectService projects,
			UserRepository users, EventRecordRepository events, Clock clock) {

		this.specifications = specifications;
		this.requirements = requirements;
		this.ia = ia;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	// =================================================================

	@Transactional(readOnly = true)
	public SpecificationsView consultar(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);

		return new SpecificationsView(
				generador(proyecto.getId()).isPresent(),
				vistas(proyecto.getId(), Specification.CASO_DE_USO),
				vistas(proyecto.getId(), Specification.HISTORIA));
	}

	/**
	 * Genera con el modelo configurado.
	 *
	 * <p>Lo que ya es regla base no se toca: se genera solo para los requisitos
	 * que no la tienen, o para los que la tienen atrasada. Sobrescribir lo
	 * aceptado seria tirar el trabajo de quien lo reviso.</p>
	 */
	@Transactional
	public List<SpecificationView> generar(String projectReadableId, GenerateRequest peticion,
			UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		SpecificationGenerator generador = generador(proyecto.getId())
				.orElseThrow(() -> new GenerationException(
						"Esta funcion necesita un modelo y este proyecto no lo tiene activo. "
								+ "Configurelo en Servicios de IA: los casos de uso y las historias "
								+ "no pueden derivarse del texto, porque la accion del actor no esta "
								+ "en ningun requisito"));

		List<Requirement> aprobados = requirements
				.findByProjectIdAndStatusOrderByReadableIdAsc(proyecto.getId(),
						RequirementStatus.APPROVED);

		if (!peticion.requirements().isEmpty()) {
			Set<String> pedidos = new HashSet<>(peticion.requirements());
			aprobados = aprobados.stream().filter(r -> pedidos.contains(r.getReadableId())).toList();
		}

		if (aprobados.isEmpty()) {
			throw new GenerationException("No hay requisitos aprobados de los que generar");
		}

		// Los que ya tienen regla base vigente se dejan en paz.
		Set<UUID> conReglaBase = new HashSet<>(
				specifications.requisitosConReglaBaseVigente(proyecto.getId(), peticion.kind()));

		List<Requirement> pendientes = aprobados.stream()
				.filter(r -> !conReglaBase.contains(r.getId()))
				.toList();

		if (pendientes.isEmpty()) {
			throw new GenerationException(
					"Todos los requisitos elegidos ya tienen una regla base vigente. Regenerar "
							+ "borraria lo que el equipo acepto; si alguno debe rehacerse, retire "
							+ "antes su regla base");
		}

		List<SpecificationView> salida = new ArrayList<>();
		int secuencia = specifications.mayorNumero(proyecto.getId(), peticion.kind());

		for (Requirement r : pendientes) {
			List<RequirementInput> entrada = List.of(entradaDe(r));

			for (SpecificationGenerator.Resultado resultado
					: generador.generar(entrada, peticion.kind())) {

				secuencia++;
				String readableId = String.format("%s-%04d",
						Specification.CASO_DE_USO.equals(peticion.kind()) ? "CU" : "HU", secuencia);

				Specification s = Specification.crear(proyecto.getId(), readableId,
						peticion.kind(), resultado.nombre(), resultado.contenido(),
						Specification.GENERADO, autor, momento);

				specifications.save(s);
				specifications.enlazar(s.getId(), r.getId(), r.getVersion());

				guardarReparos(s, momento);
				salida.add(vistaDe(s));
			}
		}

		registrar("SPECS_GENERATED", proyecto.getId(), autor,
				salida.size() + " de " + peticion.kind(), momento);

		return salida;
	}

	/** Alta escrita desde cero, sin modelo. */
	@Transactional
	public SpecificationView crear(String projectReadableId, SpecificationRequest peticion,
			UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		int secuencia = specifications.mayorNumero(proyecto.getId(), peticion.kind()) + 1;
		String readableId = String.format("%s-%04d",
				Specification.CASO_DE_USO.equals(peticion.kind()) ? "CU" : "HU", secuencia);

		Specification s = Specification.crear(proyecto.getId(), readableId, peticion.kind(),
				peticion.name(), peticion.fields(), Specification.HUMANO, autor, momento);

		specifications.save(s);
		enlazarRequisitos(proyecto.getId(), s, peticion.requirements());
		guardarReparos(s, momento);

		registrar("SPEC_CREATED", proyecto.getId(), autor, readableId, momento);
		return vistaDe(s);
	}

	/**
	 * Modificacion.
	 *
	 * <p>Devuelve la especificacion a borrador y la marca como escrita por una
	 * persona: quien la edita pasa a responder de ella aunque partiera de lo
	 * generado. Y retira la regla base, porque lo aceptado era otro texto.</p>
	 */
	@Transactional
	public SpecificationView editar(String projectReadableId, String readableId,
			SpecificationRequest peticion, UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Specification s = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		s.editar(peticion.name(), peticion.fields(), momento);
		guardarReparos(s, momento);

		registrar("SPEC_EDITED", proyecto.getId(), autor, readableId, momento);
		return vistaDe(s);
	}

	/**
	 * El equipo acepta, y pasa a ser regla base.
	 *
	 * <p>Se acepta aunque tenga reparos: la comprobacion informa y decide el
	 * equipo. Lo que queda es constancia de cuantos habia.</p>
	 */
	@Transactional
	public SpecificationView aceptar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirEquipo(projectReadableId, autor);
		Specification s = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		int reparos = specifications.reparosDe(s.getId());
		s.aceptar(autor, reparos, momento);

		// Se refresca la version de los requisitos: la regla base vale para el
		// texto que se acepto, y desde ahora se compara con ese.
		specifications.refrescarVersiones(s.getId());

		registrar("SPEC_ACCEPTED", proyecto.getId(), autor,
				readableId + (reparos > 0 ? " con " + reparos + " reparos" : ""), momento);

		return vistaDe(s);
	}

	@Transactional
	public SpecificationView descartar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirEquipo(projectReadableId, autor);
		Specification s = buscar(proyecto.getId(), readableId);

		s.descartar(Instant.now(clock));
		registrar("SPEC_DISCARDED", proyecto.getId(), autor, readableId, Instant.now(clock));

		return vistaDe(s);
	}

	/** Retira la regla base para poder regenerar. */
	@Transactional
	public SpecificationView retirarReglaBase(String projectReadableId, String readableId,
			UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Specification s = buscar(proyecto.getId(), readableId);

		s.retirarReglaBase(Instant.now(clock));
		registrar("SPEC_BASELINE_WITHDRAWN", proyecto.getId(), autor, readableId,
				Instant.now(clock));

		return vistaDe(s);
	}

	@Transactional
	public void eliminar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirEquipo(projectReadableId, autor);
		Specification s = buscar(proyecto.getId(), readableId);

		if (s.esReglaBase()) {
			throw new GenerationException(
					"Es regla base y no puede eliminarse: el equipo la acepto. Retire antes su "
							+ "condicion de regla base, o descartela, que conserva su historia");
		}

		registrar("SPEC_DELETED", proyecto.getId(), autor, readableId, Instant.now(clock));
		specifications.delete(s);
	}

	// =================================================================

	/**
	 * Comprueba y guarda los reparos.
	 *
	 * <p>Se aplica igual a lo generado, lo editado y lo escrito desde cero: lo
	 * escrito a mano no es mas fiable, solo tiene otro autor.</p>
	 */
	private void guardarReparos(Specification s, Instant momento) {
		specifications.borrarReparos(s.getId());

		Object campos = JsonParser.analizar(s.getFields());
		if (!(campos instanceof Map<?, ?> mapa)) {
			specifications.anadirReparo(UUID.randomUUID(), s.getId(), "fields",
					"Los campos no pueden leerse: no forman un documento valido", true, "RULE",
					momento);
			return;
		}

		for (SpecificationValidator.Reparo r : SpecificationValidator.revisar(mapa, s.getKind())) {
			specifications.anadirReparo(UUID.randomUUID(), s.getId(), r.campo(), r.motivo(),
					r.grave(), "RULE", momento);
		}
	}

	private void enlazarRequisitos(UUID projectId, Specification s, List<String> readableIds) {
		if (readableIds == null) {
			return;
		}
		for (String id : readableIds) {
			requirements.findByProjectIdAndReadableId(projectId, id)
					.ifPresent(r -> specifications.enlazar(s.getId(), r.getId(), r.getVersion()));
		}
	}

	private Optional<SpecificationGenerator> generador(UUID projectId) {
		return ia.generadorDeEspecificaciones(projectId, AiFeature.GENERATE_SPECS,
				Duration.ofSeconds(60));
	}

	private RequirementInput entradaDe(Requirement r) {
		return new RequirementInput(r.getReadableId(), r.getSourceId(), r.getKind().name(),
				r.getName(), r.getStatement(), r.getVerification(), r.getActor());
	}

	private List<SpecificationView> vistas(UUID projectId, String kind) {
		return specifications.findByProjectIdAndKindOrderByReadableIdAsc(projectId, kind).stream()
				.map(this::vistaDe).toList();
	}

	private SpecificationView vistaDe(Specification s) {
		List<String> requisitos = specifications.requisitosDe(s.getId()).stream()
				.map(requirements::findById)
				.filter(Optional::isPresent)
				.map(o -> {
					Requirement r = o.get();
					return r.getSourceId() == null || r.getSourceId().isBlank()
							? r.getReadableId() : r.getSourceId();
				})
				.toList();

		List<IssueView> reparos = specifications.reparosVistaDe(s.getId()).stream()
				.map(f -> new IssueView((String) f[0], (String) f[1], (Boolean) f[2], (String) f[3]))
				.toList();

		String quien = s.getAcceptedBy() == null ? null
				: users.findById(s.getAcceptedBy()).map(User::getUsername).orElse(null);

		return new SpecificationView(s.getReadableId(), s.getKind(),
				Specification.CASO_DE_USO.equals(s.getKind())
						? "Caso de uso expandido" : "Historia de usuario",
				s.getName(), s.getFields(), s.getOrigin(), etiquetaOrigen(s.getOrigin()),
				s.getStatus(), s.esReglaBase(),
				s.esReglaBase() && specifications.estaAtrasada(s.getId()),
				s.getVersion(), quien, s.getAcceptedWithIssues(), requisitos, reparos,
				s.getUpdatedAt());
	}

	private String etiquetaOrigen(String origen) {
		return switch (origen) {
			case Specification.GENERADO -> "Generada por el modelo";
			case Specification.EDITADO -> "Generada y editada";
			default -> "Escrita por una persona";
		};
	}

	private Specification buscar(UUID projectId, String readableId) {
		return specifications.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new GenerationException("No existe esa especificacion"));
	}

	private Project exigirEquipo(String readableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(readableId, solicitante);

		if (!projects.rolesEn(proyecto.getId(), solicitante).contains(ProjectRole.TEAM_MEMBER)) {
			throw new ProjectAccessException(
					"Los casos de uso y las historias los produce y acepta el equipo de desarrollo");
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle,
			Instant momento) {

		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
