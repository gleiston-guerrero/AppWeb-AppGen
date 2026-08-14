package org.slcp.service.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slcp.service.deliverables.DeliverableRepository;
import org.slcp.service.domain.Activity;
import org.slcp.service.domain.Component;
import org.slcp.service.domain.Deliverable;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.MembershipStatus;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectMembership;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Resource;
import org.slcp.service.domain.ResourceKind;
import org.slcp.service.domain.Task;
import org.slcp.service.domain.TaskStatus;
import org.slcp.service.domain.TimeEntry;
import org.slcp.service.domain.User;
import org.slcp.service.planning.PlanningContracts.ActivityRequest;
import org.slcp.service.planning.PlanningContracts.ActivityView;
import org.slcp.service.planning.PlanningContracts.AssignedResourceView;
import org.slcp.service.planning.PlanningContracts.ComponentRequest;
import org.slcp.service.planning.PlanningContracts.ComponentView;
import org.slcp.service.planning.PlanningContracts.DeliverableBreakdownView;
import org.slcp.service.planning.PlanningContracts.PlanView;
import org.slcp.service.planning.PlanningContracts.ResourceAssignmentRequest;
import org.slcp.service.planning.PlanningContracts.ResourceRequest;
import org.slcp.service.planning.PlanningContracts.ResourceView;
import org.slcp.service.planning.PlanningContracts.TaskRequest;
import org.slcp.service.planning.PlanningContracts.TaskView;
import org.slcp.service.planning.PlanningContracts.TimeEntryRequest;
import org.slcp.service.planning.PlanningContracts.TimeEntryView;
import org.slcp.service.planning.PlanningContracts.WorkloadView;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectMembershipRepository;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descomposicion del trabajo: componentes, tareas, actividades y recursos.
 *
 * <p>Planificar --- crear y ordenar la descomposicion --- corresponde al
 * facilitador. Ejecutar --- dar actividades por hechas y registrar horas ---
 * corresponde a quien tiene la tarea asignada, que es un miembro del equipo.</p>
 *
 * <p>El avance no se calcula aqui: se consulta a las vistas de la base de datos.
 * Dos calculos del mismo hecho acaban discrepando, y entonces ninguno es
 * fiable.</p>
 */
@Service
public class PlanningService {

	private final ComponentRepository components;
	private final TaskRepository tasks;
	private final ActivityRepository activities;
	private final TimeEntryRepository times;
	private final ResourceRepository resources;
	private final ProgressRepository progress;
	private final DeliverableRepository deliverables;
	private final ProjectMembershipRepository memberships;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final Clock clock;

	public PlanningService(ComponentRepository components, TaskRepository tasks,
			ActivityRepository activities, TimeEntryRepository times, ResourceRepository resources,
			ProgressRepository progress, DeliverableRepository deliverables,
			ProjectMembershipRepository memberships, ProjectService projects, UserRepository users,
			EventRecordRepository events, Clock clock) {

		this.components = components;
		this.tasks = tasks;
		this.activities = activities;
		this.times = times;
		this.resources = resources;
		this.progress = progress;
		this.deliverables = deliverables;
		this.memberships = memberships;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	// =================================================================
	// Consulta del plan completo
	// =================================================================

	/**
	 * Plan del proyecto con su avance calculado.
	 *
	 * <p>Se devuelve entero en una sola peticion. La descomposicion de un proyecto
	 * cabe holgadamente, y pedirla por partes obligaria a la interfaz a encadenar
	 * llamadas para mostrar un arbol que se lee de una vez.</p>
	 */
	@Transactional(readOnly = true)
	public PlanView plan(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);
		UUID id = proyecto.getId();

		Map<UUID, Object[]> avanceTarea = indexar(progress.tareas(id));
		Map<UUID, Object[]> avanceComponente = indexar(progress.componentes(id));
		Map<UUID, Object[]> avanceEntregable = indexar(progress.entregables(id));

		Map<UUID, String> nombres = new HashMap<>();
		users.findAllById(tareasAsignadas(id)).forEach(u -> nombres.put(u.getId(), u.getUsername()));

		List<DeliverableBreakdownView> desglose = new ArrayList<>();

		for (Deliverable d : deliverables.findByProjectIdOrderByReadableIdAsc(id)) {
			List<ComponentView> vistaComponentes = new ArrayList<>();

			for (Component c : components.findByDeliverableIdOrderByReadableIdAsc(d.getId())) {
				List<TaskView> vistaTareas = new ArrayList<>();

				for (Task t : tasks.findByComponentIdOrderByReadableIdAsc(c.getId())) {
					vistaTareas.add(vistaDe(t, avanceTarea.get(t.getId()), nombres));
				}

				Object[] a = avanceComponente.get(c.getId());
				vistaComponentes.add(new ComponentView(c.getReadableId(), c.getName(),
						c.getDescription(), entero(a, 1), decimal(a, 2), decimal(a, 3),
						c.puedeEliminarse(), vistaTareas, c.getUpdatedAt()));
			}

			Object[] a = avanceEntregable.get(d.getId());
			desglose.add(new DeliverableBreakdownView(d.getReadableId(), d.getName(),
					d.getStatus().name(), entero(a, 1), decimal(a, 2), decimal(a, 3),
					vistaComponentes));
		}

		List<Object[]> total = progress.proyecto(id);
		Object[] p = total.isEmpty() ? null : total.get(0);

		return new PlanView(entero(p, 0), decimal(p, 1), decimal(p, 2), desglose,
				recursos(id), cargaPorPersona(id, avanceTarea));
	}

	// =================================================================
	// Planificacion, que corresponde al facilitador
	// =================================================================

	@Transactional
	public ComponentView crearComponente(String projectReadableId, String deliverableReadableId,
			ComponentRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Deliverable entregable = deliverables
				.findByProjectIdAndReadableId(proyecto.getId(), deliverableReadableId)
				.orElseThrow(() -> new PlanningException("No existe ese entregable en el proyecto"));

		Instant momento = Instant.now(clock);
		String readableId = String.format("COM-%04d", components.mayorNumero(proyecto.getId()) + 1);

		Component componente = Component.crear(proyecto.getId(), entregable.getId(), readableId,
				peticion.name(), peticion.description(), autor, momento);

		components.save(componente);
		registrar("COMPONENT_CREATED", proyecto.getId(), autor,
				readableId + " en " + entregable.getReadableId(), momento);

		return vistaSimple(componente);
	}

	@Transactional
	public ComponentView editarComponente(String projectReadableId, String readableId,
			ComponentRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Component componente = buscarComponente(proyecto.getId(), readableId);

		componente.editar(peticion.name(), peticion.description(), Instant.now(clock));
		registrar("COMPONENT_EDITED", proyecto.getId(), autor, readableId, Instant.now(clock));

		return vistaSimple(componente);
	}

	@Transactional
	public void eliminarComponente(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Component componente = buscarComponente(proyecto.getId(), readableId);

		List<Task> suyas = tasks.findByComponentIdOrderByReadableIdAsc(componente.getId());
		if (!suyas.isEmpty()) {
			throw new PlanningException("El componente tiene " + suyas.size()
					+ " tareas y no puede eliminarse. Elimine antes sus tareas, o conservelo: "
					+ "borrarlo con ellas dejaria trabajo registrado sin sitio al que pertenecer");
		}

		registrar("COMPONENT_DELETED", proyecto.getId(), autor, readableId, Instant.now(clock));
		components.delete(componente);
	}

	@Transactional
	public TaskView crearTarea(String projectReadableId, String componentReadableId,
			TaskRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Component componente = buscarComponente(proyecto.getId(), componentReadableId);
		Instant momento = Instant.now(clock);

		UUID asignado = resolverAsignado(proyecto, peticion.assignee());
		String readableId = String.format("TAR-%04d", tasks.mayorNumero(proyecto.getId()) + 1);

		Task tarea = Task.crear(proyecto.getId(), componente.getId(), readableId, peticion.name(),
				peticion.description(), peticion.plannedEffort(), asignado, autor, momento);

		tasks.save(tarea);
		registrar("TASK_CREATED", proyecto.getId(), autor,
				readableId + " en " + componente.getReadableId(), momento);

		return vistaDe(tarea, null, Map.of());
	}

	@Transactional
	public TaskView editarTarea(String projectReadableId, String readableId, TaskRequest peticion,
			UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Task tarea = buscarTarea(proyecto.getId(), readableId);

		tarea.editar(peticion.name(), peticion.description(), peticion.plannedEffort(),
				resolverAsignado(proyecto, peticion.assignee()), Instant.now(clock));

		registrar("TASK_EDITED", proyecto.getId(), autor, readableId, Instant.now(clock));
		return vistaDe(tarea, null, Map.of());
	}

	@Transactional
	public void eliminarTarea(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Task tarea = buscarTarea(proyecto.getId(), readableId);

		List<Activity> suyas = activities.findByTaskIdOrderByReadableIdAsc(tarea.getId());
		boolean conHoras = suyas.stream()
				.anyMatch(a -> times.horasDe(a.getId()).signum() > 0);

		if (conHoras) {
			throw new PlanningException(
					"Esta tarea tiene horas registradas y no puede eliminarse: borrarla haria "
							+ "desaparecer trabajo que alguien dedico");
		}

		suyas.forEach(activities::delete);
		tasks.recursosDe(tarea.getId()).forEach(r -> tasks.retirarRecurso(tarea.getId(), r));

		registrar("TASK_DELETED", proyecto.getId(), autor, readableId, Instant.now(clock));
		tasks.delete(tarea);
	}

	// =================================================================
	// Ejecucion, que corresponde a quien tiene la tarea
	// =================================================================

	@Transactional
	public TaskView transitarTarea(String projectReadableId, String readableId, String destino,
			UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Task tarea = buscarTarea(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		TaskStatus estado = TaskStatus.valueOf(destino);
		tarea.transitarA(estado, autor, momento);

		// El componente queda marcado en cuanto algo suyo se termina: eliminarlo
		// despues borraria trabajo dado por hecho.
		if (estado == TaskStatus.DONE) {
			components.findById(tarea.getComponentId()).ifPresent(c -> {
				c.marcarDecidido();
				components.save(c);
			});
		}

		registrar("TASK_" + estado.name(), proyecto.getId(), autor, readableId, momento);
		return vistaDe(tarea, null, Map.of());
	}

	@Transactional
	public ActivityView crearActividad(String projectReadableId, String taskReadableId,
			ActivityRequest peticion, UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Task tarea = buscarTarea(proyecto.getId(), taskReadableId);
		Instant momento = Instant.now(clock);

		String readableId = String.format("ACT-%04d", activities.mayorNumero(proyecto.getId()) + 1);
		Activity actividad = Activity.crear(proyecto.getId(), tarea.getId(), readableId,
				peticion.name(), peticion.plannedEffort() == null ? 1 : peticion.plannedEffort(),
				autor, momento);

		activities.save(actividad);
		registrar("ACTIVITY_CREATED", proyecto.getId(), autor,
				readableId + " en " + tarea.getReadableId(), momento);

		return vistaDe(actividad);
	}

	@Transactional
	public ActivityView editarActividad(String projectReadableId, String readableId,
			ActivityRequest peticion, UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Activity actividad = buscarActividad(proyecto.getId(), readableId);

		actividad.editar(peticion.name(), peticion.plannedEffort(), Instant.now(clock));
		registrar("ACTIVITY_EDITED", proyecto.getId(), autor, readableId, Instant.now(clock));

		return vistaDe(actividad);
	}

	/**
	 * Retira un asiento de horas.
	 *
	 * <p>Solo puede retirarlo quien lo anoto. Un asiento no se corrige --- se
	 * anota otro ---, pero un asiento equivocado si ha de poder quitarse: no
	 * existen las horas negativas, de modo que sin esto una cifra mal tecleada se
	 * quedaria para siempre.</p>
	 */
	@Transactional
	public ActivityView retirarHoras(String projectReadableId, String activityReadableId,
			UUID entryId, UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Activity actividad = buscarActividad(proyecto.getId(), activityReadableId);

		TimeEntry asiento = times.findById(entryId)
				.filter(e -> e.getActivityId().equals(actividad.getId()))
				.orElseThrow(() -> new PlanningException("No existe ese asiento en la actividad"));

		if (!asiento.getPersonId().equals(autor)) {
			throw new PlanningException(
					"Solo puede retirar un asiento de horas quien lo anoto: son sus horas, y "
							+ "quitarlas por el falsearia su dedicacion");
		}

		times.delete(asiento);
		registrar("TIME_REMOVED", proyecto.getId(), autor,
				asiento.getHours() + " h de " + activityReadableId, Instant.now(clock));

		return vistaDe(actividad);
	}

	@Transactional
	public ActivityView marcarActividad(String projectReadableId, String readableId, boolean hecha,
			UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Activity actividad = buscarActividad(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		actividad.marcar(hecha, momento);
		registrar(hecha ? "ACTIVITY_DONE" : "ACTIVITY_REOPENED", proyecto.getId(), autor,
				readableId, momento);

		return vistaDe(actividad);
	}

	@Transactional
	public void eliminarActividad(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirEquipo(projectReadableId, autor);
		Activity actividad = buscarActividad(proyecto.getId(), readableId);

		if (times.horasDe(actividad.getId()).signum() > 0) {
			throw new PlanningException(
					"Esta actividad tiene horas registradas y no puede eliminarse: borrarla haria "
							+ "desaparecer trabajo que alguien dedico");
		}

		registrar("ACTIVITY_DELETED", proyecto.getId(), autor, readableId, Instant.now(clock));
		activities.delete(actividad);
	}

	/**
	 * Registra horas dedicadas a una actividad.
	 *
	 * <p>Las anota quien las dedico, y a su nombre: permitir anotarlas por otro
	 * haria que la carga por persona dejase de reflejar quien trabajo.</p>
	 */
	@Transactional
	public ActivityView registrarHoras(String projectReadableId, String readableId,
			TimeEntryRequest peticion, UUID autor) {

		Project proyecto = exigirEquipo(projectReadableId, autor);
		Activity actividad = buscarActividad(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		times.save(TimeEntry.de(proyecto.getId(), actividad.getId(), autor, peticion.hours(),
				peticion.workedOn(), peticion.note(), momento));

		registrar("TIME_LOGGED", proyecto.getId(), autor,
				peticion.hours() + " h en " + readableId, momento);

		return vistaDe(actividad);
	}

	// =================================================================
	// Recursos materiales
	// =================================================================

	@Transactional
	public ResourceView crearRecurso(String projectReadableId, ResourceRequest peticion, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		String readableId = String.format("REC-%04d", resources.mayorNumero(proyecto.getId()) + 1);
		Resource recurso = Resource.crear(proyecto.getId(), readableId, peticion.name(),
				claseDe(peticion.kind()), peticion.unit(), peticion.quantity(), peticion.notes(),
				autor, momento);

		resources.save(recurso);
		registrar("RESOURCE_CREATED", proyecto.getId(), autor, readableId, momento);

		return vistaDe(recurso, 0);
	}

	@Transactional
	public ResourceView editarRecurso(String projectReadableId, String readableId,
			ResourceRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Resource recurso = buscarRecurso(proyecto.getId(), readableId);

		recurso.editar(peticion.name(), claseDe(peticion.kind()), peticion.unit(),
				peticion.quantity(), peticion.notes(), Instant.now(clock));

		registrar("RESOURCE_EDITED", proyecto.getId(), autor, readableId, Instant.now(clock));
		return vistaDe(recurso, resources.asignaciones(recurso.getId()));
	}

	@Transactional
	public void eliminarRecurso(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Resource recurso = buscarRecurso(proyecto.getId(), readableId);

		long enUso = resources.asignaciones(recurso.getId());
		if (enUso > 0) {
			throw new PlanningException("Este recurso esta asignado a " + enUso
					+ " tareas y no puede eliminarse. Retirelo de ellas primero");
		}

		registrar("RESOURCE_DELETED", proyecto.getId(), autor, readableId, Instant.now(clock));
		resources.delete(recurso);
	}

	@Transactional
	public TaskView asignarRecurso(String projectReadableId, String taskReadableId,
			ResourceAssignmentRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Task tarea = buscarTarea(proyecto.getId(), taskReadableId);
		Resource recurso = buscarRecurso(proyecto.getId(), peticion.resource());

		tasks.asignarRecurso(tarea.getId(), recurso.getId(), peticion.quantity());
		registrar("RESOURCE_ASSIGNED", proyecto.getId(), autor,
				recurso.getReadableId() + " a " + taskReadableId, Instant.now(clock));

		return vistaDe(tarea, null, Map.of());
	}

	@Transactional
	public void retirarRecurso(String projectReadableId, String taskReadableId,
			String resourceReadableId, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Task tarea = buscarTarea(proyecto.getId(), taskReadableId);
		Resource recurso = buscarRecurso(proyecto.getId(), resourceReadableId);

		tasks.retirarRecurso(tarea.getId(), recurso.getId());
		registrar("RESOURCE_UNASSIGNED", proyecto.getId(), autor,
				recurso.getReadableId() + " de " + taskReadableId, Instant.now(clock));
	}

	// =================================================================
	// Construccion de vistas
	// =================================================================

	private TaskView vistaDe(Task t, Object[] avance, Map<UUID, String> nombres) {
		List<ActivityView> suyas = activities.findByTaskIdOrderByReadableIdAsc(t.getId()).stream()
				.map(this::vistaDe).toList();

		List<AssignedResourceView> recursosDe = tasks.recursosDe(t.getId()).stream()
				.map(resources::findById)
				.filter(java.util.Optional::isPresent)
				.map(java.util.Optional::get)
				.map(r -> new AssignedResourceView(r.getReadableId(), r.getName(),
						r.getKind().getEtiqueta(), r.getUnit(), r.getQuantity()))
				.toList();

		String asignado = t.getAssigneeId() == null ? null
				: nombres.getOrDefault(t.getAssigneeId(),
						users.findById(t.getAssigneeId()).map(User::getUsername).orElse(null));

		String nombreCompleto = t.getAssigneeId() == null ? null
				: users.findById(t.getAssigneeId()).map(User::getFullName).orElse(null);

		String quienTermino = t.getDoneBy() == null ? null
				: users.findById(t.getDoneBy()).map(User::getUsername).orElse(null);

		return new TaskView(t.getReadableId(), t.getName(), t.getDescription(),
				t.getPlannedEffort(), asignado, nombreCompleto, t.getStatus().name(),
				t.getStatus().getEtiqueta(), quienTermino,
				decimal(avance, 2), decimal(avance, 3), suyas, recursosDe, t.getUpdatedAt());
	}

	private ActivityView vistaDe(Activity a) {
		List<TimeEntryView> asientos = times.findByActivityIdOrderByWorkedOnAsc(a.getId()).stream()
				.map(e -> new TimeEntryView(e.getId().toString(),
						users.findById(e.getPersonId()).map(User::getUsername).orElse("desconocido"),
						e.getHours(), e.getWorkedOn(), e.getNote()))
				.toList();

		return new ActivityView(a.getReadableId(), a.getName(), a.getPlannedEffort(), a.isDone(),
				a.getDoneAt(), times.horasDe(a.getId()), asientos);
	}

	private ComponentView vistaSimple(Component c) {
		return new ComponentView(c.getReadableId(), c.getName(), c.getDescription(), 0,
				BigDecimal.ZERO, BigDecimal.ZERO, c.puedeEliminarse(), List.of(), c.getUpdatedAt());
	}

	private ResourceView vistaDe(Resource r, long asignaciones) {
		return new ResourceView(r.getReadableId(), r.getName(), r.getKind().name(),
				r.getKind().getEtiqueta(), r.getUnit(), r.getQuantity(), r.getNotes(), asignaciones);
	}

	private List<ResourceView> recursos(UUID projectId) {
		return resources.findByProjectIdOrderByReadableIdAsc(projectId).stream()
				.map(r -> vistaDe(r, resources.asignaciones(r.getId())))
				.toList();
	}

	/**
	 * Carga de trabajo por persona.
	 *
	 * <p>Es el informe que responde si el proyecto depende de alguien en
	 * particular. Se calcula sobre las tareas asignadas y su avance, no sobre las
	 * horas: quien tiene mucho asignado y poco dedicado es precisamente el caso
	 * que interesa ver.</p>
	 */
	private List<WorkloadView> cargaPorPersona(UUID projectId, Map<UUID, Object[]> avanceTarea) {
		Map<UUID, int[]> acumulado = new HashMap<>();
		Map<UUID, BigDecimal[]> medidas = new HashMap<>();

		for (Task t : tasks.findByProjectIdOrderByReadableIdAsc(projectId)) {
			if (t.getAssigneeId() == null) {
				continue;
			}
			int[] cuenta = acumulado.computeIfAbsent(t.getAssigneeId(), k -> new int[2]);
			cuenta[0]++;
			cuenta[1] += t.getPlannedEffort();

			Object[] a = avanceTarea.get(t.getId());
			BigDecimal[] m = medidas.computeIfAbsent(t.getAssigneeId(),
					k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });

			m[0] = m[0].add(decimal(a, 2).multiply(BigDecimal.valueOf(t.getPlannedEffort())));
			m[1] = m[1].add(decimal(a, 3));
		}

		List<WorkloadView> salida = new ArrayList<>();
		acumulado.forEach((personaId, cuenta) -> {
			User persona = users.findById(personaId).orElse(null);
			if (persona == null) {
				return;
			}
			BigDecimal[] m = medidas.get(personaId);
			BigDecimal avance = cuenta[1] == 0 ? BigDecimal.ZERO
					: m[0].divide(BigDecimal.valueOf(cuenta[1]), 4, RoundingMode.HALF_UP);

			salida.add(new WorkloadView(persona.getUsername(), persona.getFullName(),
					cuenta[0], cuenta[1], m[1], avance));
		});

		salida.sort((a, b) -> Integer.compare(b.effort(), a.effort()));
		return salida;
	}

	// =================================================================

	private Map<UUID, Object[]> indexar(List<Object[]> filas) {
		Map<UUID, Object[]> mapa = new HashMap<>();
		for (Object[] fila : filas) {
			mapa.put((UUID) fila[0], fila);
		}
		return mapa;
	}

	private int entero(Object[] fila, int columna) {
		if (fila == null || fila.length <= columna || fila[columna] == null) {
			return 0;
		}
		return ((Number) fila[columna]).intValue();
	}

	private BigDecimal decimal(Object[] fila, int columna) {
		if (fila == null || fila.length <= columna || fila[columna] == null) {
			return BigDecimal.ZERO;
		}
		return new BigDecimal(fila[columna].toString()).setScale(4, RoundingMode.HALF_UP);
	}

	private List<UUID> tareasAsignadas(UUID projectId) {
		return tasks.findByProjectIdOrderByReadableIdAsc(projectId).stream()
				.map(Task::getAssigneeId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
	}

	/**
	 * Resuelve a quien se asigna la tarea.
	 *
	 * <p>Ha de ser miembro del equipo del proyecto. La base lo impone tambien
	 * (WBS-09); aqui se comprueba para poder explicarlo.</p>
	 */
	private UUID resolverAsignado(Project proyecto, String username) {
		if (username == null || username.isBlank()) {
			return null;
		}

		User persona = users.findByUsernameIgnoreCase(username.trim())
				.orElseThrow(() -> new PlanningException(
						"No existe ninguna cuenta con ese identificador"));

		boolean esDelEquipo = memberships
				.findByProjectIdAndUserIdAndStatus(proyecto.getId(), persona.getId(),
						MembershipStatus.ACTIVE)
				.stream()
				.anyMatch(m -> m.getProjectRole() == ProjectRole.TEAM_MEMBER);

		if (!esDelEquipo) {
			throw new PlanningException(persona.getUsername()
					+ " no es miembro del equipo de este proyecto. Incorporelo antes de asignarle "
					+ "trabajo");
		}
		return persona.getId();
	}

	private ResourceKind claseDe(String valor) {
		if (valor == null || valor.isBlank()) {
			return ResourceKind.OTHER;
		}
		try {
			return ResourceKind.valueOf(valor);
		} catch (IllegalArgumentException e) {
			throw new PlanningException("Clase de recurso no reconocida: " + valor);
		}
	}

	private Component buscarComponente(UUID projectId, String readableId) {
		return components.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new PlanningException("No existe ese componente en el proyecto"));
	}

	private Task buscarTarea(UUID projectId, String readableId) {
		return tasks.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new PlanningException("No existe esa tarea en el proyecto"));
	}

	private Activity buscarActividad(UUID projectId, String readableId) {
		return activities.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new PlanningException("No existe esa actividad en el proyecto"));
	}

	private Resource buscarRecurso(UUID projectId, String readableId) {
		return resources.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new PlanningException("No existe ese recurso en el proyecto"));
	}

	private Project exigirFacilitador(String readableId, UUID solicitante) {
		return exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
	}

	private Project exigirEquipo(String readableId, UUID solicitante) {
		return exigirRol(readableId, solicitante, ProjectRole.TEAM_MEMBER);
	}

	private Project exigirRol(String readableId, UUID solicitante, ProjectRole rol) {
		Project proyecto = projects.exigirAccesoPublico(readableId, solicitante);

		if (!projects.rolesEn(proyecto.getId(), solicitante).contains(rol)) {
			throw new ProjectAccessException("Esta operacion corresponde al "
					+ rol.getEtiqueta().toLowerCase() + " del proyecto");
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle,
			Instant momento) {

		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
