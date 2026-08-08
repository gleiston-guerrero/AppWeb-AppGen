package org.slcp.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.LoginIdentifier;
import org.slcp.service.domain.RefreshToken;
import org.slcp.service.domain.User;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.LoginIdentifierRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio, renovacion y cierre de sesion.
 *
 * <p>Realiza FUN-03, FUN-04, FUN-06 y SEC-03. Toda entrada, salida e intento
 * fallido deja evento en el almacen de solo anexado.</p>
 */
@Service
public class AuthenticationService {

	private final UserRepository users;
	private final LoginIdentifierRepository identifiers;
	private final RefreshTokenRepository refreshTokens;
	private final EventRecordRepository events;
	private final PasswordEncoder passwordEncoder;
	private final SessionProperties propiedades;
	private final LoginThrottle throttle;
	private final Clock clock;

	public AuthenticationService(UserRepository users, LoginIdentifierRepository identifiers,
			RefreshTokenRepository refreshTokens, EventRecordRepository events,
			PasswordEncoder passwordEncoder, SessionProperties propiedades,
			LoginThrottle throttle, Clock clock) {
		this.users = users;
		this.identifiers = identifiers;
		this.refreshTokens = refreshTokens;
		this.events = events;
		this.passwordEncoder = passwordEncoder;
		this.propiedades = propiedades;
		this.throttle = throttle;
		this.clock = clock;
	}

	/**
	 * Resultado de abrir o renovar una sesion.
	 *
	 * <p>No incluye el token de acceso: lo firma el controlador, que es quien
	 * conoce el emisor. Incluirlo aqui obligaria a arrastrar un campo que siempre
	 * llegaria vacio.</p>
	 */
	public record Sesion(User usuario, String refreshToken, Instant expiraEn) {
	}

	/**
	 * Comprueba las credenciales y emite la sesion.
	 *
	 * @throws AuthenticationFailedException si las credenciales no son validas o
	 *                                       la cuenta no esta operativa
	 */
	@Transactional
	public Sesion iniciar(LoginRequest peticion, String origen) {
		Instant momento = Instant.now(clock);

		// El limite de intentos es el control que impide enumerar cuentas y probar
		// contrasenas. Se comprueba antes que nada, para que un bloqueo no consuma
		// trabajo de verificacion.
		Optional<LoginFailure> bloqueo = throttle.comprobar(peticion.identifier(), origen);
		if (bloqueo.isPresent()) {
			registrar("SESSION_THROTTLED", null, peticion.identifier(), origen, momento);
			throw new AuthenticationFailedException(bloqueo.get());
		}

		Optional<User> encontrado = identifiers.resolver(peticion.identifier())
				.map(LoginIdentifier::getUserId)
				.flatMap(users::findById);

		if (encontrado.isEmpty()) {
			throw fallo(LoginFailure.UNKNOWN_IDENTIFIER, null, peticion.identifier(), origen, momento, true);
		}

		User usuario = encontrado.get();

		// Sin verificador, la cuenta procede de una invitacion sin completar.
		if (usuario.getPasswordVerifier() == null) {
			throw fallo(LoginFailure.REGISTRATION_INCOMPLETE, usuario.getId(),
					usuario.getUsername(), origen, momento, false);
		}

		if (!passwordEncoder.matches(peticion.password(), usuario.getPasswordVerifier())) {
			throw fallo(LoginFailure.BAD_PASSWORD, usuario.getId(),
					usuario.getUsername(), origen, momento, true);
		}

		// La contrasena era correcta: lo que sigue no es un intento fallido de
		// acceso sino una cuenta que no esta operativa, y no debe penalizarse.
		LoginFailure impedimento = switch (usuario.getStatus()) {
			case PENDING_APPROVAL -> LoginFailure.PENDING_APPROVAL;
			case REJECTED -> LoginFailure.REJECTED;
			case DECOMMISSIONED -> LoginFailure.DECOMMISSIONED;
			case ACTIVE -> null;
		};
		if (impedimento != null) {
			throw fallo(impedimento, usuario.getId(), usuario.getUsername(), origen, momento, false);
		}

		throttle.anotarAcierto(peticion.identifier());

		String refresh = TokenHasher.generar();
		refreshTokens.save(RefreshToken.emitir(TokenHasher.resumir(refresh), usuario.getId(),
				momento, momento.plus(propiedades.refreshTtl())));

		registrar("SESSION_OPENED", usuario.getId(), usuario.getUsername(), origen, momento);

		return new Sesion(usuario, refresh, momento.plus(propiedades.accessTtl()));
	}

	/**
	 * Deja constancia del fallo y lo devuelve para lanzarlo.
	 *
	 * @param penaliza si el fallo cuenta para el limite de intentos. Un
	 *                 impedimento de estado no lo hace: la persona acerto su
	 *                 contrasena y bloquearla seria castigarla por esperar una
	 *                 aprobacion que no depende de ella
	 */
	private AuthenticationFailedException fallo(LoginFailure motivo, java.util.UUID sujeto,
			String etiqueta, String origen, Instant momento, boolean penaliza) {
		if (penaliza) {
			throttle.anotarFallo(etiqueta, origen);
		}
		registrar("SESSION_DENIED_" + motivo.name(), sujeto, etiqueta, origen, momento);
		return new AuthenticationFailedException(motivo);
	}

	/**
	 * Renueva la sesion y sustituye el token de renovacion por otro.
	 *
	 * <p>La sustitucion es deliberada: si un token robado se emplease, su uso
	 * invalidaria el de la persona legitima y el problema se manifestaria en lugar
	 * de pasar inadvertido.</p>
	 */
	@Transactional
	public Sesion renovar(String refreshAportado, String origen) {
		Instant momento = Instant.now(clock);

		RefreshToken vigente = refreshTokens.findByTokenHash(TokenHasher.resumir(refreshAportado))
				.filter(t -> t.estaVigente(momento))
				.orElseThrow(() -> new AuthenticationFailedException(LoginFailure.UNKNOWN_IDENTIFIER));

		User usuario = users.findById(vigente.getUserId())
				.filter(User::puedeIniciarSesion)
				.orElseThrow(() -> new AuthenticationFailedException(LoginFailure.UNKNOWN_IDENTIFIER));

		vigente.revocar(momento, "ROTATED");

		String nuevo = TokenHasher.generar();
		refreshTokens.save(RefreshToken.emitir(TokenHasher.resumir(nuevo), usuario.getId(),
				momento, momento.plus(propiedades.refreshTtl())));

		registrar("SESSION_REFRESHED", usuario.getId(), usuario.getUsername(), origen, momento);

		return new Sesion(usuario, nuevo, momento.plus(propiedades.accessTtl()));
	}

	/**
	 * Cierra la sesion revocando el token en el servidor.
	 *
	 * <p>Borrar la cookie no basta: un token copiado seguiria sirviendo. SEC-03
	 * exige revocacion en el servidor.</p>
	 */
	@Transactional
	public void cerrar(String refreshAportado, String origen) {
		if (refreshAportado == null || refreshAportado.isBlank()) {
			return;
		}
		Instant momento = Instant.now(clock);
		refreshTokens.findByTokenHash(TokenHasher.resumir(refreshAportado)).ifPresent(token -> {
			token.revocar(momento, "LOGOUT");
			registrar("SESSION_CLOSED", token.getUserId(), "", origen, momento);
		});
	}

	private void registrar(String tipo, java.util.UUID sujeto, String etiqueta, String origen,
			Instant momento) {
		events.save(EventRecord.de(tipo, "User",
				sujeto != null ? sujeto : new java.util.UUID(0L, 0L),
				sujeto, etiqueta, "origen=" + origen, momento));
	}
}
