package org.slcp.service.projects;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.MembershipStatus;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectMembership;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.User;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.LoginIdentifierRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proyectos y equipo.
 *
 * <p>Realiza FUN-07, FUN-09, FUN-10 y ROL-01. El alcance de cuanto se consulta
 * se deriva siempre de la membresia de quien pregunta.</p>
 */
@Service
public class ProjectService {

	private final ProjectRepository projects;
	private final ProjectMembershipRepository memberships;
	private final UserRepository users;
	private final LoginIdentifierRepository identifiers;
	private final EventRecordRepository events;
	private final Clock clock;

	public ProjectService(ProjectRepository projects, ProjectMembershipRepository memberships,
			UserRepository users, LoginIdentifierRepository identifiers,
			EventRecordRepository events, Clock clock) {
		this.projects = projects;
		this.memberships = memberships;
		this.users = users;
		this.identifiers = identifiers;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Crea un proyecto y hace facilitador a quien lo crea.
	 *
	 * <p>La membresia se crea a la vez a proposito: un proyecto sin facilitador
	 * seria un proyecto que nadie puede gestionar.</p>
	 */
	@Transactional
	public ProjectView crear(ProjectRequest peticion, UUID creador) {
		Instant momento = Instant.now(clock);
		// Por el mayor usado, no por la cuenta: contar devuelve un numero ya tomado
		// en cuanto se elimina un proyecto.
		long secuencia = projects.mayorNumero() + 1;

		Project proyecto = Project.crear(peticion.name(), peticion.purpose(), creador, secuencia, momento);
		projects.save(proyecto);

		// El facilitador recibe ademas el rol de miembro del equipo: organiza y
		// tambien ejecuta. Se le asignan los dos de forma expresa en lugar de hacer
		// que uno implique al otro, porque los informes de trabajo enumeran a quien
		// ejecuta, y un rol implicito dejaria fuera de esa lista a quien si esta
		// haciendo el trabajo.
		memberships.save(ProjectMembership.activa(proyecto.getId(), creador,
				ProjectRole.PROJECT_FACILITATOR, momento));
		memberships.save(ProjectMembership.activa(proyecto.getId(), creador,
				ProjectRole.TEAM_MEMBER, momento));

		registrar("PROJECT_CREATED", proyecto.getId(), creador, proyecto.getName(), momento);

		return vista(proyecto, creador);
	}

	/** Proyectos donde quien pregunta tiene membresia activa. */
	@Transactional(readOnly = true)
	public List<ProjectView> mios(UUID userId) {
		return projects.deLaPersona(userId).stream().map(p -> vista(p, userId)).toList();
	}

	/**
	 * Modifica los datos del proyecto. Corresponde a su facilitador.
	 */
	@Transactional
	public ProjectView editar(String readableId, ProjectRequest peticion, UUID solicitante) {
		Project proyecto = exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
		Instant momento = Instant.now(clock);

		proyecto.editar(peticion.name(), peticion.purpose());
		registrar("PROJECT_EDITED", proyecto.getId(), solicitante, proyecto.getName(), momento);

		return vista(proyecto, solicitante);
	}

	/**
	 * Retira el proyecto del servicio, o lo devuelve a el.
	 *
	 * <p>Conforme a ADM-01 el contenido permanece. Un proyecto retirado deja de
	 * admitir trabajo y sigue siendo consultable, que es lo que distingue retirar
	 * de eliminar.</p>
	 */
	@Transactional
	public ProjectView cambiarEstado(String readableId, boolean activo, UUID solicitante) {
		Project proyecto = exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
		Instant momento = Instant.now(clock);

		if (activo) {
			proyecto.reincorporar();
		} else {
			proyecto.retirar();
		}
		registrar(activo ? "PROJECT_REINSTATED" : "PROJECT_DECOMMISSIONED",
				proyecto.getId(), solicitante, proyecto.getName(), momento);

		return vista(proyecto, solicitante);
	}

	/**
	 * Elimina el proyecto.
	 *
	 * <p>Solo mientras este vacio. Un proyecto con requisitos guarda decisiones
	 * que alguien tomo, y borrarlas no es una operacion de mantenimiento sino una
	 * perdida: para eso esta la retirada del servicio.</p>
	 */
	@Transactional
	public void eliminar(String readableId, UUID solicitante) {
		Project proyecto = exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
		Instant momento = Instant.now(clock);
		String nombre = proyecto.getName();

		// El rastro se deja antes de borrar: despues no habria proyecto al que
		// referirlo, y un borrado sin constancia es indistinguible de que el
		// proyecto no hubiera existido nunca.
		registrar("PROJECT_DELETED", proyecto.getId(), solicitante, nombre, momento);

		memberships.findByProjectIdAndStatus(proyecto.getId(), MembershipStatus.ACTIVE)
				.forEach(memberships::delete);
		projects.delete(proyecto);
	}

	/** Equipo del proyecto. Exige membresia activa en el. */
	@Transactional(readOnly = true)
	public List<MemberView> equipo(String readableId, UUID solicitante) {
		Project proyecto = exigirAcceso(readableId, solicitante);

		return memberships.findByProjectIdAndStatus(proyecto.getId(), MembershipStatus.ACTIVE).stream()
				.map(m -> users.findById(m.getUserId())
						.map(u -> new MemberView(u.getUsername(), u.getFullName(), u.getEmail(),
								m.getProjectRole().name(), m.getProjectRole().getEtiqueta(),
								m.getStatus().name()))
						.orElse(null))
				.filter(v -> v != null)
				.toList();
	}

	/**
	 * Incorpora a alguien al equipo con el rol que fija el facilitador.
	 *
	 * <p>El rol no lo elige quien entra: viene con la designacion. La segregacion
	 * de ROL-06 la comprueba el dominio para poder explicarla, y la impone ademas
	 * la base de datos para que ninguna via la sortee.</p>
	 */
	@Transactional
	public MemberView incorporar(String readableId, MemberRequest peticion, UUID solicitante) {
		Project proyecto = exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);

		User persona = identifiers.resolver(peticion.identifier())
				.flatMap(li -> users.findById(li.getUserId()))
				.orElseThrow(() -> new ProjectAccessException(
						"No existe ninguna cuenta con ese nombre de usuario ni con ese correo. "
								+ "La incorporacion por invitacion a quien aun no tiene cuenta llegara "
								+ "en el incremento siguiente"));

		List<ProjectMembership> actuales = memberships.findByProjectIdAndUserIdAndStatus(
				proyecto.getId(), persona.getId(), MembershipStatus.ACTIVE);

		for (ProjectMembership m : actuales) {
			if (m.getProjectRole() == peticion.role()) {
				throw new ProjectAccessException("Esa persona ya tiene ese rol en el proyecto");
			}
			if (m.getProjectRole().incompatibleCon(peticion.role())) {
				throw new ProjectAccessException(
						m.getProjectRole().motivoDeIncompatibilidad(peticion.role()) + ". "
								+ persona.getUsername() + " ya es "
								+ m.getProjectRole().getEtiqueta() + " en este proyecto");
			}
		}

		Instant momento = Instant.now(clock);
		incorporarCon(proyecto.getId(), persona.getId(), peticion.role(), momento);

		registrar("MEMBERSHIP_GRANTED", proyecto.getId(), solicitante,
				persona.getUsername() + " como " + peticion.role().name(), momento);

		return new MemberView(persona.getUsername(), persona.getFullName(), persona.getEmail(),
				peticion.role().name(), peticion.role().getEtiqueta(), MembershipStatus.ACTIVE.name());
	}

	/**
	 * Da de alta la membresia, con el rol acompanante que corresponda.
	 *
	 * <p>Quien recibe el papel de facilitador recibe tambien el de miembro del
	 * equipo: en esta plataforma el facilitador organiza y ademas ejecuta. Se
	 * expone para que la incorporacion por invitacion siga el mismo criterio y no
	 * cada via el suyo.</p>
	 */
	@Transactional
	public void incorporarCon(UUID projectId, UUID userId, ProjectRole rol, Instant momento) {
		memberships.save(ProjectMembership.activa(projectId, userId, rol, momento));

		if (rol == ProjectRole.PROJECT_FACILITATOR) {
			boolean yaEsEquipo = memberships
					.findByProjectIdAndUserIdAndStatus(projectId, userId, MembershipStatus.ACTIVE)
					.stream().anyMatch(m -> m.getProjectRole() == ProjectRole.TEAM_MEMBER);

			if (!yaEsEquipo) {
				memberships.save(ProjectMembership.activa(projectId, userId,
						ProjectRole.TEAM_MEMBER, momento));
			}
		}
	}

	/**
	 * Cambia el rol de alguien del equipo.
	 *
	 * <p>Se retira el rol anterior y se concede el nuevo, en lugar de modificar la
	 * fila: la membresia anterior existio y su rastro cuenta desde cuando y hasta
	 * cuando alguien tuvo ese papel, que es lo que hace falta para saber quien
	 * podia hacer que en cada momento.</p>
	 */
	@Transactional
	public MemberView cambiarRol(String readableId, String username, ProjectRole nuevo,
			UUID solicitante) {

		Project proyecto = exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
		User persona = buscarPersona(username);
		Instant momento = Instant.now(clock);

		List<ProjectMembership> actuales = memberships.findByProjectIdAndUserIdAndStatus(
				proyecto.getId(), persona.getId(), MembershipStatus.ACTIVE);

		if (actuales.isEmpty()) {
			throw new ProjectAccessException(persona.getUsername() + " no participa en este proyecto");
		}

		// Nadie puede quedarse sin facilitador: si se cambia el rol del ultimo, el
		// proyecto se queda sin quien planifique ni pueda incorporar a nadie mas.
		if (nuevo != ProjectRole.PROJECT_FACILITATOR
				&& tieneRol(actuales, ProjectRole.PROJECT_FACILITATOR)
				&& contarConRol(proyecto.getId(), ProjectRole.PROJECT_FACILITATOR) <= 1) {

			throw new ProjectAccessException(
					"Es el unico facilitador del proyecto. Incorpore antes a otro: sin facilitador "
							+ "nadie podria planificar ni incorporar a nadie mas");
		}

		actuales.forEach(m -> {
			m.retirar();
			memberships.save(m);
		});

		incorporarCon(proyecto.getId(), persona.getId(), nuevo, momento);

		registrar("MEMBERSHIP_ROLE_CHANGED", proyecto.getId(), solicitante,
				persona.getUsername() + " pasa a " + nuevo.name(), momento);

		return new MemberView(persona.getUsername(), persona.getFullName(), persona.getEmail(),
				nuevo.name(), nuevo.getEtiqueta(), MembershipStatus.ACTIVE.name());
	}

	/**
	 * Retira a alguien del equipo.
	 *
	 * <p>La membresia se marca retirada y no se borra: lo que esa persona hizo
	 * mientras participaba sigue constando a su nombre, y borrar su pertenencia
	 * dejaria actos sin autor reconocible.</p>
	 */
	@Transactional
	public void retirarDelEquipo(String readableId, String username, UUID solicitante) {
		Project proyecto = exigirRol(readableId, solicitante, ProjectRole.PROJECT_FACILITATOR);
		User persona = buscarPersona(username);
		Instant momento = Instant.now(clock);

		List<ProjectMembership> actuales = memberships.findByProjectIdAndUserIdAndStatus(
				proyecto.getId(), persona.getId(), MembershipStatus.ACTIVE);

		if (actuales.isEmpty()) {
			throw new ProjectAccessException(persona.getUsername() + " no participa en este proyecto");
		}

		if (tieneRol(actuales, ProjectRole.PROJECT_FACILITATOR)
				&& contarConRol(proyecto.getId(), ProjectRole.PROJECT_FACILITATOR) <= 1) {

			throw new ProjectAccessException(
					"Es el unico facilitador del proyecto y no puede retirarse. Incorpore antes a otro");
		}

		actuales.forEach(m -> {
			m.retirar();
			memberships.save(m);
		});

		registrar("MEMBERSHIP_REVOKED", proyecto.getId(), solicitante, persona.getUsername(), momento);
	}

	private boolean tieneRol(List<ProjectMembership> membresias, ProjectRole rol) {
		return membresias.stream().anyMatch(m -> m.getProjectRole() == rol);
	}

	private long contarConRol(UUID projectId, ProjectRole rol) {
		return memberships.findByProjectIdAndStatus(projectId, MembershipStatus.ACTIVE).stream()
				.filter(m -> m.getProjectRole() == rol)
				.count();
	}

	private User buscarPersona(String username) {
		return users.findByUsernameIgnoreCase(username)
				.orElseThrow(() -> new ProjectAccessException(
						"No existe ninguna cuenta con ese identificador"));
	}

	/** Roles de la persona en el proyecto. Vacio si no participa. */
	@Transactional(readOnly = true)
	public List<ProjectRole> rolesEn(UUID projectId, UUID userId) {
		return memberships.findByProjectIdAndUserIdAndStatus(projectId, userId, MembershipStatus.ACTIVE)
				.stream().map(ProjectMembership::getProjectRole).toList();
	}

	/**
	 * Comprueba el acceso y devuelve el proyecto.
	 *
	 * <p>Se expone para que otros servicios del mismo proyecto resuelvan el
	 * alcance por la misma via, y no cada uno por la suya.</p>
	 */
	@Transactional(readOnly = true)
	public Project exigirAccesoPublico(String readableId, UUID solicitante) {
		return exigirAcceso(readableId, solicitante);
	}

	private Project exigirAcceso(String readableId, UUID solicitante) {
		Project proyecto = projects.findByReadableId(readableId)
				.orElseThrow(() -> new ProjectAccessException("No existe ese proyecto"));

		if (rolesEn(proyecto.getId(), solicitante).isEmpty()) {
			// El mensaje es el mismo que el de proyecto inexistente a proposito: quien
			// no participa no debe poder averiguar que proyectos existen.
			throw new ProjectAccessException("No existe ese proyecto");
		}
		return proyecto;
	}

	private Project exigirRol(String readableId, UUID solicitante, ProjectRole exigido) {
		Project proyecto = exigirAcceso(readableId, solicitante);

		if (!rolesEn(proyecto.getId(), solicitante).contains(exigido)) {
			throw new ProjectAccessException(
					"Esta operacion corresponde al " + exigido.getEtiqueta().toLowerCase());
		}
		return proyecto;
	}

	/**
	 * Deja constancia de un acto sobre el proyecto.
	 *
	 * <p>Se recoge en un metodo porque la llamada es larga y se repite: escrita a
	 * mano en cada sitio, basta olvidar un argumento para que un acto quede sin
	 * rastro, y la falta no se nota hasta que alguien pregunta quien hizo que.</p>
	 */
	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle, Instant momento) {
		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}

	private ProjectView vista(Project p, UUID userId) {
		List<String> roles = rolesEn(p.getId(), userId).stream().map(Enum::name).toList();
		int tamano = memberships.findByProjectIdAndStatus(p.getId(), MembershipStatus.ACTIVE).size();

		return new ProjectView(p.getReadableId(), p.getName(), p.getPurpose(),
				p.getStatus().name(), p.getCreatedAt(), roles, tamano);
	}
}
