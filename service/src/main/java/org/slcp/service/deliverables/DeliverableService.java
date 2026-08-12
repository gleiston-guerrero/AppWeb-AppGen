package org.slcp.service.deliverables;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slcp.service.deliverables.DeliverableContracts.DeliverableRequest;
import org.slcp.service.deliverables.DeliverableContracts.DeliverableView;
import org.slcp.service.deliverables.DeliverableContracts.LinkableRequirement;
import org.slcp.service.deliverables.DeliverableContracts.LinkedRequirement;
import org.slcp.service.domain.Deliverable;
import org.slcp.service.domain.DeliverableStatus;
import org.slcp.service.domain.EventRecord;
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
 * Entregables de un proyecto.
 *
 * <p>La planificacion corresponde al facilitador: crea los entregables y los
 * enlaza con los requisitos aprobados que realizan. La aceptacion corresponde al
 * propietario del producto, y es lo que cierra esos requisitos (RQM-14).</p>
 *
 * <p>El cierre no se calcula aqui sino que se consulta a la vista de la base de
 * datos. Dos calculos del mismo hecho acaban discrepando, y entonces ninguno es
 * fiable.</p>
 */
@Service
public class DeliverableService {

	private final DeliverableRepository deliverables;
	private final RequirementRepository requirements;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final Clock clock;

	public DeliverableService(DeliverableRepository deliverables, RequirementRepository requirements,
			ProjectService projects, UserRepository users, EventRecordRepository events, Clock clock) {
		this.deliverables = deliverables;
		this.requirements = requirements;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	// =================================================================
	// Consulta
	// =================================================================

	@Transactional(readOnly = true)
	public List<DeliverableView> listar(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);
		Set<UUID> cerrados = new HashSet<>(deliverables.requisitosCerrados(proyecto.getId()));

		return deliverables.findByProjectIdOrderByReadableIdAsc(proyecto.getId()).stream()
				.map(d -> vista(d, cerrados))
				.toList();
	}

	/**
	 * Requisitos aprobados que pueden enlazarse.
	 *
	 * <p>Solo aprobados: enlazar trabajo a un requisito que aun puede cambiar
	 * daria por decidido lo que no lo esta.</p>
	 */
	@Transactional(readOnly = true)
	public List<LinkableRequirement> enlazables(String projectReadableId, String deliverableId,
			UUID solicitante) {

		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);

		Set<UUID> yaEnlazados = deliverableId == null || deliverableId.isBlank()
				? Set.of()
				: new HashSet<>(deliverables.requisitosDe(
						buscar(proyecto.getId(), deliverableId).getId()));

		return requirements
				.findByProjectIdAndStatusOrderByReadableIdAsc(proyecto.getId(), RequirementStatus.APPROVED)
				.stream()
				.map(r -> new LinkableRequirement(r.getReadableId(), r.getName(), r.getStatement(),
						yaEnlazados.contains(r.getId())))
				.toList();
	}

	// =================================================================
	// Planificacion, que corresponde al facilitador
	// =================================================================

	@Transactional
	public DeliverableView crear(String projectReadableId, DeliverableRequest peticion, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		long secuencia = deliverables.countByProjectId(proyecto.getId()) + 1;
		Deliverable entregable = Deliverable.crear(proyecto.getId(),
				String.format("ENT-%04d-v1", secuencia),
				peticion.name(), peticion.description(), peticion.acceptance(), autor, momento);

		deliverables.save(entregable);
		enlazarTodos(proyecto, entregable, peticion.requirementIds());

		registrar("DELIVERABLE_CREATED", proyecto.getId(), autor, entregable.getReadableId(), momento);
		return vista(entregable, cerradosDe(proyecto));
	}

	@Transactional
	public DeliverableView editar(String projectReadableId, String readableId,
			DeliverableRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Deliverable entregable = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		entregable.editar(peticion.name(), peticion.description(), peticion.acceptance(), momento);

		// Los enlaces se rehacen: quien modifica el entregable puede haber cambiado
		// tambien que requisitos realiza, y conservar los antiguos dejaria enlaces
		// que nadie decidio.
		deliverables.requisitosDe(entregable.getId())
				.forEach(r -> deliverables.desenlazar(entregable.getId(), r));
		enlazarTodos(proyecto, entregable, peticion.requirementIds());

		registrar("DELIVERABLE_EDITED", proyecto.getId(), autor, entregable.getReadableId(), momento);
		return vista(entregable, cerradosDe(proyecto));
	}

	@Transactional
	public void eliminar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Deliverable entregable = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		if (!entregable.puedeEliminarse()) {
			throw new DeliverableException(
					"Este entregable fue entregado o aceptado y no puede eliminarse. Su aceptacion "
							+ "cerro requisitos, y borrarlo dejaria ese cierre sin fundamento");
		}

		registrar("DELIVERABLE_DELETED", proyecto.getId(), autor, entregable.getReadableId(), momento);
		deliverables.delete(entregable);
	}

	/**
	 * Cambia el estado del entregable.
	 *
	 * <p>Aceptar y devolver corresponden al propietario del producto; avanzar el
	 * trabajo, a quien lo hace.</p>
	 */
	@Transactional
	public DeliverableView transitar(String projectReadableId, String readableId,
			String destino, UUID autor) {

		DeliverableStatus estado = DeliverableStatus.valueOf(destino);
		ProjectRole exigido = (estado == DeliverableStatus.ACCEPTED
				|| estado == DeliverableStatus.REJECTED)
						? ProjectRole.PRODUCT_OWNER
						: ProjectRole.TEAM_MEMBER;

		Project proyecto = exigirRol(projectReadableId, autor, exigido);
		Deliverable entregable = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		if (estado == DeliverableStatus.ACCEPTED
				&& deliverables.requisitosDe(entregable.getId()).isEmpty()) {
			throw new DeliverableException(
					"Este entregable no realiza ningun requisito. Aceptarlo no cerraria nada: "
							+ "enlacelo antes con los requisitos que realiza");
		}

		entregable.transitarA(estado, autor, momento);
		registrar("DELIVERABLE_" + estado.name(), proyecto.getId(), autor,
				entregable.getReadableId(), momento);

		return vista(entregable, cerradosDe(proyecto));
	}

	// =================================================================

	private void enlazarTodos(Project proyecto, Deliverable entregable, List<String> ids) {
		if (ids == null) {
			return;
		}
		for (String readableId : ids) {
			Requirement requisito = requirements
					.findByProjectIdAndReadableId(proyecto.getId(), readableId)
					.orElseThrow(() -> new DeliverableException(
							"No existe el requisito " + readableId + " en este proyecto"));

			if (requisito.getStatus() != RequirementStatus.APPROVED) {
				throw new DeliverableException("El requisito " + readableId + " no esta aprobado. "
						+ "Solo se enlaza trabajo a requisitos aprobados");
			}
			deliverables.enlazar(entregable.getId(), requisito.getId());
		}
	}

	private Set<UUID> cerradosDe(Project proyecto) {
		return new HashSet<>(deliverables.requisitosCerrados(proyecto.getId()));
	}

	private DeliverableView vista(Deliverable d, Set<UUID> cerrados) {
		List<LinkedRequirement> enlazados = deliverables.requisitosDe(d.getId()).stream()
				.map(id -> requirements.findById(id)
						.map(r -> new LinkedRequirement(r.getReadableId(), r.getStatement(),
								cerrados.contains(r.getId())))
						.orElse(null))
				.filter(r -> r != null)
				.toList();

		String quien = d.getAcceptedBy() == null ? null
				: users.findById(d.getAcceptedBy()).map(User::getUsername).orElse("desconocido");

		return new DeliverableView(d.getReadableId(), d.getName(), d.getDescription(),
				d.getAcceptance(), d.getStatus().name(), etiqueta(d.getStatus()), d.getVersion(),
				d.puedeEliminarse(), quien, d.getAcceptedAt(), enlazados, d.getUpdatedAt());
	}

	private String etiqueta(DeliverableStatus estado) {
		return switch (estado) {
			case PLANNED -> "Planificado";
			case IN_PROGRESS -> "En curso";
			case DELIVERED -> "Entregado";
			case ACCEPTED -> "Aceptado";
			case REJECTED -> "Devuelto";
		};
	}

	private Deliverable buscar(UUID projectId, String readableId) {
		return deliverables.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new DeliverableException(
						"No existe ese entregable en el proyecto"));
	}

	private Project exigirFacilitador(String readableId, UUID solicitante) {
		return exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
	}

	private Project exigirRol(String readableId, UUID solicitante, ProjectRole rol) {
		Project proyecto = projects.exigirAccesoPublico(readableId, solicitante);
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
