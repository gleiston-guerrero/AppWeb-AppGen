package org.slcp.service.registration;

import java.time.Clock;
import java.time.Instant;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de solicitudes de cuenta.
 *
 * <p>Realiza FUN-15: la solicitud queda en estado pendiente de aprobacion y no
 * otorga capacidad alguna. Realiza tambien TRC-24, porque toda solicitud deja
 * su evento en el almacen de solo anexado.</p>
 */
@Service
public class RegistrationService {

	private final UserRepository users;
	private final LoginIdentifierRepository identifiers;
	private final EventRecordRepository events;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public RegistrationService(UserRepository users, LoginIdentifierRepository identifiers,
			EventRecordRepository events, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.identifiers = identifiers;
		this.events = events;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Registra la solicitud.
	 *
	 * @throws RegistrationConflictException si el nombre de usuario o el correo
	 *                                       ya estan en uso
	 */
	@Transactional
	public RegistrationResponse solicitar(RegistrationRequest peticion) {
		// La comprobacion recae sobre el espacio de nombres compartido y no sobre
		// cada columna por separado: FUN-03 exige que un nombre de usuario tampoco
		// pueda coincidir con el correo de otra cuenta.
		if (identifiers.resolver(peticion.username()).isPresent()) {
			throw new RegistrationConflictException(
					"Ese nombre de usuario ya esta en uso como nombre o como correo de otra cuenta");
		}
		if (identifiers.resolver(peticion.email()).isPresent()) {
			throw new RegistrationConflictException(
					"Ese correo ya esta en uso como correo o como nombre de otra cuenta");
		}

		Instant momento = Instant.now(clock);
		long secuencia = users.count() + 1;

		// La contrasena se deriva aqui y en claro no llega mas alla de este punto.
		String verificador = passwordEncoder.encode(peticion.password());

		User usuario = User.solicitar(peticion.username(), peticion.email(),
				peticion.fullName(), verificador, secuencia, momento);
		users.save(usuario);

		// Los identificadores de acceso NO se insertan aqui: los crea la propia base
		// de datos mediante disparador. Cualquier via que cree una cuenta sin pasar
		// por este servicio dejaria, de otro modo, a la persona sin poder acceder, y
		// el defecto no se manifestaria hasta el primer intento de inicio de sesion.

		events.save(EventRecord.de(
				"USER_REGISTRATION_REQUESTED",
				"User",
				usuario.getId(),
				null,
				usuario.getUsername(),
				"Solicitud de registro como facilitador de proyectos",
				momento));

		return new RegistrationResponse(
				usuario.getReadableId(),
				usuario.getUsername(),
				usuario.getStatus().name(),
				usuario.getCreatedAt(),
				"Solicitud registrada. Queda pendiente de aprobacion por el administrador "
						+ "de la plataforma y no otorga capacidad alguna hasta entonces.");
	}
}
