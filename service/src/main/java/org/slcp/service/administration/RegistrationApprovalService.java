package org.slcp.service.administration;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.User;
import org.slcp.service.domain.UserStatus;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprobacion y rechazo de solicitudes de registro.
 *
 * <p>Realiza FUN-16 y ROL-05. Toda decision deja evento con su autor, y el
 * rechazo exige motivo.</p>
 */
@Service
public class RegistrationApprovalService {

	private final UserRepository users;
	private final EventRecordRepository events;
	private final Clock clock;

	public RegistrationApprovalService(UserRepository users, EventRecordRepository events, Clock clock) {
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	/** Solicitudes a la espera de decision, de la mas antigua a la mas reciente. */
	@Transactional(readOnly = true)
	public List<PendingRegistration> pendientes() {
		return users.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING_APPROVAL).stream()
				.map(u -> new PendingRegistration(u.getReadableId(), u.getUsername(),
						u.getEmail(), u.getFullName(), u.getCreatedAt()))
				.toList();
	}

	/**
	 * Aplica la decision del administrador.
	 *
	 * @param readableId  identificador legible de la solicitud
	 * @param decision    aprobacion o rechazo con su motivo
	 * @param actorId     quien decide
	 * @param actorLabel  como consta quien decide
	 */
	@Transactional
	public ApprovalResult decidir(String readableId, ApprovalDecision decision,
			UUID actorId, String actorLabel) {

		if (decision.rechazoSinMotivo()) {
			throw new InvalidDecisionException(
					"El rechazo exige un motivo, para que quien lo recibe sepa a que atenerse");
		}

		User usuario = users.findByReadableId(readableId)
				.orElseThrow(() -> new RegistrationNotFoundException(readableId));

		if (usuario.getStatus() != UserStatus.PENDING_APPROVAL) {
			throw new InvalidDecisionException("Esa solicitud ya fue resuelta y su estado es "
					+ usuario.getStatus() + ". Una decision no se revisa: se toma una nueva");
		}

		Instant momento = Instant.now(clock);
		String mensaje;

		if (Boolean.TRUE.equals(decision.approved())) {
			usuario.aprobar();
			mensaje = "Solicitud aprobada. La cuenta queda operativa como facilitador de proyectos.";
		} else {
			usuario.rechazar();
			mensaje = "Solicitud rechazada. El motivo consta en el registro.";
		}

		events.save(EventRecord.de(
				Boolean.TRUE.equals(decision.approved()) ? "REGISTRATION_APPROVED" : "REGISTRATION_REJECTED",
				"User", usuario.getId(), actorId, actorLabel,
				decision.reason() == null ? "" : decision.reason(), momento));

		return new ApprovalResult(usuario.getReadableId(), usuario.getUsername(),
				usuario.getStatus().name(), momento, mensaje);
	}
}
