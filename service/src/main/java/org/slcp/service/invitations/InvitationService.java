package org.slcp.service.invitations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.auth.TokenHasher;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.Invitation;
import org.slcp.service.domain.MembershipStatus;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectMembership;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.User;
import org.slcp.service.invitations.InvitationContracts.Camino;
import org.slcp.service.invitations.InvitationContracts.CompletionRequest;
import org.slcp.service.invitations.InvitationContracts.InvitationPreview;
import org.slcp.service.invitations.InvitationContracts.InviteRequest;
import org.slcp.service.invitations.InvitationContracts.InviteResult;
import org.slcp.service.invitations.InvitationContracts.JoinResult;
import org.slcp.service.invitations.InvitationContracts.PendingInvite;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectMembershipRepository;
import org.slcp.service.projects.ProjectRepository;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.LoginIdentifierRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incorporacion de personas al equipo de un proyecto.
 *
 * <p>Hay tres caminos y los distingue el estado de la persona invitada, no una
 * eleccion de quien invita. Quien invita indica siempre lo mismo: un correo y
 * un rol.</p>
 *
 * <p><strong>El enlace solo se devuelve cuando la entrega falla.</strong> Si el
 * correo sale, quien invita no ve el enlace, y con ello se preserva la
 * verificacion del correo de INV-03: solo quien lee el buzon puede usarlo. Si la
 * entrega falla, se devuelve para que la incorporacion no quede bloqueada por
 * una caida del servidor de correo, y esa entrega manual queda registrada.</p>
 */
@Service
public class InvitationService {

	/** Plazo del enlace. Valor por defecto de Q-16. */
	private static final Duration PLAZO = Duration.ofDays(7);

	private final InvitationRepository invitations;
	private final ProjectRepository projects;
	private final ProjectMembershipRepository memberships;
	private final UserRepository users;
	private final LoginIdentifierRepository identifiers;
	private final EventRecordRepository events;
	private final PasswordEncoder passwordEncoder;
	private final InvitationMailer mailer;
	private final Clock clock;

	public InvitationService(InvitationRepository invitations, ProjectRepository projects,
			ProjectMembershipRepository memberships, UserRepository users,
			LoginIdentifierRepository identifiers, EventRecordRepository events,
			PasswordEncoder passwordEncoder, InvitationMailer mailer, Clock clock) {
		this.invitations = invitations;
		this.projects = projects;
		this.memberships = memberships;
		this.users = users;
		this.identifiers = identifiers;
		this.events = events;
		this.passwordEncoder = passwordEncoder;
		this.mailer = mailer;
		this.clock = clock;
	}

	// =================================================================
	// Quien invita
	// =================================================================

	/**
	 * Invita a un correo con el rol indicado.
	 *
	 * <p>Decide el camino por si mismo: si la direccion ya tiene cuenta activa,
	 * se le propone la membresia y debe aceptarla; si no la tiene, se emite un
	 * enlace para que complete su registro.</p>
	 */
	@Transactional
	public InviteResult invitar(String projectReadableId, InviteRequest peticion, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);
		Instant momento = Instant.now(clock);
		String email = peticion.email().trim().toLowerCase();

		Optional<User> existente = identifiers.resolver(email)
				.flatMap(li -> users.findById(li.getUserId()));

		if (existente.isPresent()) {
			return proponerA(existente.get(), proyecto, peticion.role(), solicitante, momento);
		}
		return emitirEnlace(proyecto, email, peticion.role(), solicitante, momento);
	}

	/** Invitaciones vigentes del proyecto. */
	@Transactional(readOnly = true)
	public List<PendingInvite> vigentes(String projectReadableId, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);

		return invitations.vigentesDelProyecto(proyecto.getId()).stream()
				.map(i -> new PendingInvite(i.getId().toString(), i.getEmail(),
						i.getProjectRole().name(), i.getProjectRole().getEtiqueta(),
						i.getCreatedAt(), i.getExpiresAt(),
						identifiers.resolver(i.getEmail()).isPresent()))
				.toList();
	}

	/** Retira una invitacion. El enlace deja de servir de inmediato. */
	@Transactional
	public void revocar(String projectReadableId, String invitationId, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);
		Instant momento = Instant.now(clock);

		Invitation invitacion = invitations.findById(UUID.fromString(invitationId))
				.filter(i -> i.getProjectId().equals(proyecto.getId()))
				.orElseThrow(() -> new InvitationException("No existe esa invitacion"));

		invitacion.revocar(momento, "Retirada por el facilitador");
		registrar("INVITATION_REVOKED", proyecto.getId(), solicitante, invitacion.getEmail(), momento);
	}

	// =================================================================
	// Quien recibe la invitacion
	// =================================================================

	/**
	 * Lo que se muestra al abrir un enlace, antes de decidir nada.
	 *
	 * <p>Realiza INV-03: a una direccion todavia no verificada no se le revela
	 * mas que el nombre del proyecto y quien invita. Ni requisitos, ni artefactos,
	 * ni quienes forman el equipo.</p>
	 */
	@Transactional(readOnly = true)
	public InvitationPreview describir(String token) {
		Instant momento = Instant.now(clock);

		Optional<Invitation> encontrada = invitations.findByTokenHash(TokenHasher.resumir(token));
		if (encontrada.isEmpty()) {
			return invalida("Ese enlace no corresponde a ninguna invitacion");
		}

		Invitation i = encontrada.get();
		String motivo = i.motivoDeRechazo(momento);
		if (!motivo.isEmpty()) {
			return invalida(motivo);
		}

		Project proyecto = projects.findById(i.getProjectId())
				.orElseThrow(() -> new InvitationException("El proyecto ya no existe"));
		String quien = users.findById(i.getInvitedBy()).map(User::getFullName).orElse("el facilitador");
		boolean requiereRegistro = identifiers.resolver(i.getEmail()).isEmpty();

		return new InvitationPreview(true, "", proyecto.getName(), proyecto.getReadableId(),
				quien, i.getEmail(), i.getProjectRole().name(), i.getProjectRole().getEtiqueta(),
				alcance(i.getProjectRole()), requiereRegistro);
	}

	/**
	 * Completa el registro desde el enlace y activa la membresia.
	 *
	 * <p>Consumir el enlace constituye la verificacion del correo (INV-03), de
	 * modo que la cuenta nace operativa sin pasar por el administrador: responde
	 * de ella el facilitador que la invito.</p>
	 */
	@Transactional
	public JoinResult completar(String token, CompletionRequest peticion) {
		Instant momento = Instant.now(clock);

		Invitation invitacion = invitations.findByTokenHash(TokenHasher.resumir(token))
				.orElseThrow(() -> new InvitationException(
						"Ese enlace no corresponde a ninguna invitacion"));

		String motivo = invitacion.motivoDeRechazo(momento);
		if (!motivo.isEmpty()) {
			throw new InvitationException(motivo);
		}

		if (identifiers.resolver(invitacion.getEmail()).isPresent()) {
			throw new InvitationException(
					"Ese correo ya tiene cuenta. Inicie sesion y acepte la invitacion desde su espacio de trabajo");
		}
		if (identifiers.resolver(peticion.username()).isPresent()) {
			throw new InvitationException(
					"Ese nombre de usuario ya esta en uso como nombre o como correo de otra cuenta");
		}

		Project proyecto = projects.findById(invitacion.getProjectId())
				.orElseThrow(() -> new InvitationException("El proyecto ya no existe"));

		// La cuenta nace operativa y SIN atribucion de facilitador: su alcance es el
		// proyecto que la origino, no la plataforma.
		User persona = User.solicitar(peticion.username(), invitacion.getEmail(),
				peticion.fullName(), passwordEncoder.encode(peticion.password()),
				users.count() + 1, momento);
		persona.aprobar();
		users.save(persona);

		memberships.save(ProjectMembership.activa(proyecto.getId(), persona.getId(),
				invitacion.getProjectRole(), momento));

		invitacion.consumir(momento);

		registrar("INVITATION_ACCEPTED", proyecto.getId(), persona.getId(),
				persona.getUsername() + " como " + invitacion.getProjectRole().name(), momento);

		return new JoinResult(proyecto.getReadableId(), proyecto.getName(),
				invitacion.getProjectRole().name(), invitacion.getProjectRole().getEtiqueta(),
				"Registro completado. Ya forma parte del equipo. Inicie sesion para empezar.");
	}

	/** Invitaciones vigentes dirigidas a quien pregunta. */
	@Transactional(readOnly = true)
	public List<PendingInvite> mias(UUID userId) {
		return users.findById(userId)
				.map(u -> invitations.vigentesPara(u.getEmail()).stream()
						.map(i -> new PendingInvite(i.getId().toString(),
								projects.findById(i.getProjectId()).map(Project::getName).orElse(""),
								i.getProjectRole().name(), i.getProjectRole().getEtiqueta(),
								i.getCreatedAt(), i.getExpiresAt(), true))
						.toList())
				.orElse(List.of());
	}

	/** Acepta una invitacion dirigida a quien ya tiene cuenta. */
	@Transactional
	public JoinResult aceptar(String invitationId, UUID userId) {
		Instant momento = Instant.now(clock);

		User persona = users.findById(userId)
				.orElseThrow(() -> new InvitationException("Cuenta no encontrada"));

		Invitation invitacion = invitations.findById(UUID.fromString(invitationId))
				.filter(i -> i.getEmail().equalsIgnoreCase(persona.getEmail()))
				.orElseThrow(() -> new InvitationException(
						"Esa invitacion no existe o no esta dirigida a su cuenta"));

		String motivo = invitacion.motivoDeRechazo(momento);
		if (!motivo.isEmpty()) {
			throw new InvitationException(motivo);
		}

		Project proyecto = projects.findById(invitacion.getProjectId())
				.orElseThrow(() -> new InvitationException("El proyecto ya no existe"));

		comprobarSegregacion(proyecto.getId(), persona, invitacion.getProjectRole());

		memberships.save(ProjectMembership.activa(proyecto.getId(), persona.getId(),
				invitacion.getProjectRole(), momento));
		invitacion.consumir(momento);

		registrar("INVITATION_ACCEPTED", proyecto.getId(), persona.getId(),
				persona.getUsername() + " como " + invitacion.getProjectRole().name(), momento);

		return new JoinResult(proyecto.getReadableId(), proyecto.getName(),
				invitacion.getProjectRole().name(), invitacion.getProjectRole().getEtiqueta(),
				"Invitacion aceptada. Ya forma parte del equipo.");
	}

	/** Rechaza una invitacion dirigida a quien pregunta. */
	@Transactional
	public void rechazar(String invitationId, UUID userId) {
		Instant momento = Instant.now(clock);

		User persona = users.findById(userId)
				.orElseThrow(() -> new InvitationException("Cuenta no encontrada"));

		Invitation invitacion = invitations.findById(UUID.fromString(invitationId))
				.filter(i -> i.getEmail().equalsIgnoreCase(persona.getEmail()))
				.orElseThrow(() -> new InvitationException(
						"Esa invitacion no existe o no esta dirigida a su cuenta"));

		invitacion.revocar(momento, "Rechazada por la persona invitada");
		registrar("INVITATION_DECLINED", invitacion.getProjectId(), userId,
				persona.getUsername(), momento);
	}

	// =================================================================
	// Interno
	// =================================================================

	private InviteResult proponerA(User persona, Project proyecto, ProjectRole rol,
			UUID solicitante, Instant momento) {

		comprobarSegregacion(proyecto.getId(), persona, rol);

		boolean yaTiene = memberships
				.findByProjectIdAndUserIdAndStatus(proyecto.getId(), persona.getId(), MembershipStatus.ACTIVE)
				.stream().anyMatch(m -> m.getProjectRole() == rol);
		if (yaTiene) {
			throw new InvitationException("Esa persona ya tiene ese rol en el proyecto");
		}

		String token = TokenHasher.generar();
		Invitation invitacion = Invitation.emitir(TokenHasher.resumir(token), proyecto.getId(),
				persona.getEmail(), rol, solicitante, momento, momento.plus(PLAZO));
		invitations.save(invitacion);

		InvitationMailer.Entrega entrega = entregar(persona.getEmail(), proyecto, rol,
				token, solicitante, true);

		registrar(entrega.enviado() ? "INVITATION_SENT" : "INVITATION_SENT_UNDELIVERED",
				proyecto.getId(), solicitante, persona.getEmail() + " como " + rol.name(), momento);

		String mensaje = persona.getFullName() + " ya tiene cuenta. "
				+ (entrega.enviado()
						? "Se le ha enviado un correo y encontrara la invitacion en su espacio de trabajo."
						: "No se pudo enviar el correo (" + entrega.detalle()
								+ "). Hagale llegar el enlace por otro medio.");

		return new InviteResult(Camino.PENDIENTE_DE_ACEPTACION, persona.getEmail(), rol.name(),
				rol.getEtiqueta(), invitacion.getExpiresAt(),
				entrega.enviado() ? null : mailer.enlaceCompleto(enlace(token)), mensaje);
	}

	private InviteResult emitirEnlace(Project proyecto, String email, ProjectRole rol,
			UUID solicitante, Instant momento) {

		String token = TokenHasher.generar();
		Invitation invitacion = Invitation.emitir(TokenHasher.resumir(token), proyecto.getId(),
				email, rol, solicitante, momento, momento.plus(PLAZO));
		invitations.save(invitacion);

		InvitationMailer.Entrega entrega = entregar(email, proyecto, rol, token, solicitante, false);

		registrar(entrega.enviado() ? "INVITATION_SENT" : "INVITATION_SENT_UNDELIVERED",
				proyecto.getId(), solicitante, email + " como " + rol.name(), momento);

		String mensaje = entrega.enviado()
				? "Invitacion enviada a " + email + ". Caduca en 7 dias y solo puede usarse una vez."
				: "No se pudo enviar el correo (" + entrega.detalle()
						+ "). Hagale llegar el enlace por otro medio. Caduca en 7 dias.";

		return new InviteResult(Camino.PENDIENTE_DE_REGISTRO, email, rol.name(), rol.getEtiqueta(),
				invitacion.getExpiresAt(),
				entrega.enviado() ? null : mailer.enlaceCompleto(enlace(token)), mensaje);
	}

	/**
	 * ROL-06 tambien al invitar.
	 *
	 * <p>Comprobarlo aqui evita emitir un enlace que fracasaria al usarse, que es
	 * peor que negarse ahora: la persona invitada no tendria por que entender por
	 * que el enlace que recibio no funciona.</p>
	 */
	private void comprobarSegregacion(UUID projectId, User persona, ProjectRole rol) {
		memberships.findByProjectIdAndUserIdAndStatus(projectId, persona.getId(), MembershipStatus.ACTIVE)
				.stream()
				.filter(m -> m.getProjectRole().incompatibleCon(rol))
				.findFirst()
				.ifPresent(m -> {
					throw new InvitationException("ROL-06: quien produce no puede aprobar en el mismo "
							+ "proyecto. " + persona.getUsername() + " ya es " + m.getProjectRole().getEtiqueta());
				});
	}

	private Project exigirFacilitador(String readableId, UUID solicitante) {
		Project proyecto = projects.findByReadableId(readableId)
				.orElseThrow(() -> new ProjectAccessException("No existe ese proyecto"));

		boolean esFacilitador = memberships
				.findByProjectIdAndUserIdAndStatus(proyecto.getId(), solicitante, MembershipStatus.ACTIVE)
				.stream().anyMatch(m -> m.getProjectRole() == ProjectRole.PROJECT_FACILITATOR);

		if (!esFacilitador) {
			// Mismo mensaje que proyecto inexistente: quien no participa no debe poder
			// averiguar que proyectos hay.
			throw new ProjectAccessException("No existe ese proyecto");
		}
		return proyecto;
	}

	private InvitationPreview invalida(String motivo) {
		return new InvitationPreview(false, motivo, "", "", "", "", "", "", "", false);
	}

	private String enlace(String token) {
		return "/invitacion/" + token;
	}

	private InvitationMailer.Entrega entregar(String destinatario, Project proyecto,
			ProjectRole rol, String token, UUID solicitante, boolean tieneCuenta) {

		String quien = users.findById(solicitante).map(User::getFullName).orElse("El facilitador");
		return mailer.enviar(destinatario, proyecto.getName(), quien, rol, enlace(token),
				(int) PLAZO.toDays(), tieneCuenta);
	}

	private String alcance(ProjectRole rol) {
		return switch (rol) {
			case PROJECT_FACILITATOR -> "Organiza el proyecto, planifica e incorpora al equipo.";
			case TEAM_MEMBER -> "Trabaja los requisitos, genera y modifica artefactos.";
			case PRODUCT_OWNER -> "Verifica y aprueba. No modifica nada.";
		};
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String etiqueta, Instant momento) {
		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, etiqueta, momento));
	}
}
