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
				.map(r -> new LinkableRequirement(r.getReadableId(), r.getSourceId(),
						r.getKind().name(), r.getKind().getEtiqueta(), r.getName(), r.getStatement(),
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

		int secuencia = deliverables.mayorNumero(proyecto.getId()) + 1;
		Deliverable entregable = Deliverable.crear(proyecto.getId(),
				String.format("ENT-%04d-v1", secuencia),
				peticion.name(), peticion.description(), peticion.acceptance(), autor, momento);

		Set<UUID> antes = cerradosDe(proyecto);

		deliverables.save(entregable);
		enlazarTodos(proyecto, entregable, peticion.requirementIds());

		registrar("DELIVERABLE_CREATED", proyecto.getId(), autor, entregable.getReadableId(), momento);

		// Enlazar un entregable nuevo a un requisito cerrado lo reabre: le queda
		// trabajo por aceptar. Anotarlo aqui evita que esa reapertura pase inadvertida.
		deliverables.flush();
		anotarCambiosDeCierre(proyecto, antes, autor, entregable, momento);

		return vista(entregable, cerradosDe(proyecto));
	}

	@Transactional
	public DeliverableView editar(String projectReadableId, String readableId,
			DeliverableRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Deliverable entregable = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		Set<UUID> antes = cerradosDe(proyecto);

		entregable.editar(peticion.name(), peticion.description(), peticion.acceptance(), momento);

		// Los enlaces se rehacen: quien modifica el entregable puede haber cambiado
		// tambien que requisitos realiza, y conservar los antiguos dejaria enlaces
		// que nadie decidio.
		deliverables.requisitosDe(entregable.getId())
				.forEach(r -> deliverables.desenlazar(entregable.getId(), r));
		enlazarTodos(proyecto, entregable, peticion.requirementIds());

		registrar("DELIVERABLE_EDITED", proyecto.getId(), autor, entregable.getReadableId(), momento);

		deliverables.flush();
		anotarCambiosDeCierre(proyecto, antes, autor, entregable, momento);

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

		// Que requisitos estaban cerrados ANTES de la transicion. Sin esta foto no
		// hay forma de saber cuales cierra esta aceptacion y cuales ya lo estaban,
		// y el registro atribuiria a este acto cierres ajenos.
		Set<UUID> antes = cerradosDe(proyecto);

		entregable.transitarA(estado, autor, momento);
		registrar("DELIVERABLE_" + estado.name(), proyecto.getId(), autor,
				entregable.getReadableId(), momento);

		// El cambio ha de estar en la base antes de volver a consultar la vista: sin
		// esto se leeria el estado anterior y el cierre no se registraria nunca.
		deliverables.flush();
		anotarCambiosDeCierre(proyecto, antes, autor, entregable, momento);

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

	/**
	 * Deja constancia de los requisitos que se cierran o se reabren.
	 *
	 * <p>El cierre sigue calculandose y no se almacena: lo que se registra no es el
	 * estado sino el hecho de haber cambiado, con su fecha y su causa. Asi puede
	 * responderse cuando se cerro un requisito --- que la vista no dice, porque
	 * solo conoce el ahora --- sin duplicar el <em>si</em> en dos sitios que
	 * puedan discrepar.</p>
	 *
	 * <p>Se registra tambien la reapertura. Un requisito que se cierra y vuelve a
	 * abrirse cuenta una historia distinta de uno que nunca se cerro, y sin este
	 * asiento ambos pareceria lo mismo.</p>
	 */
	private void anotarCambiosDeCierre(Project proyecto, Set<UUID> antes, UUID autor,
			Deliverable causa, Instant momento) {

		Set<UUID> despues = cerradosDe(proyecto);

		for (UUID id : despues) {
			if (!antes.contains(id)) {
				registrarRequisito("REQUIREMENT_CLOSED", id, autor,
						"cerrado al aceptarse " + causa.getReadableId(), momento);
			}
		}
		for (UUID id : antes) {
			if (!despues.contains(id)) {
				registrarRequisito("REQUIREMENT_REOPENED", id, autor,
						"reabierto al cambiar " + causa.getReadableId(), momento);
			}
		}
	}

	/** Asiento referido al requisito, no al proyecto: el sujeto del hecho es aquel. */
	private void registrarRequisito(String tipo, UUID requirementId, UUID actorId,
			String detalle, Instant momento) {

		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		String cual = requirements.findById(requirementId)
				.map(Requirement::getReadableId).orElse(requirementId.toString());

		events.save(EventRecord.de(tipo, "Requirement", requirementId, actorId, quien,
				cual + ": " + detalle, momento));
	}

	private Set<UUID> cerradosDe(Project proyecto) {
		return new HashSet<>(deliverables.requisitosCerrados(proyecto.getId()));
	}

	private DeliverableView vista(Deliverable d, Set<UUID> cerrados) {
		List<LinkedRequirement> enlazados = deliverables.requisitosDe(d.getId()).stream()
				.map(id -> requirements.findById(id)
						.map(r -> new LinkedRequirement(r.getReadableId(), r.getSourceId(),
								r.getKind().name(), r.getKind().getEtiqueta(), r.getName(),
								r.getStatement(), cerrados.contains(r.getId())))
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
