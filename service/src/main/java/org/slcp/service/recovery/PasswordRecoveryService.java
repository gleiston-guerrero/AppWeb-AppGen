package org.slcp.service.recovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.auth.LoginFailure;
import org.slcp.service.auth.LoginThrottle;
import org.slcp.service.auth.RefreshTokenRepository;
import org.slcp.service.auth.TokenHasher;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.LoginIdentifier;
import org.slcp.service.domain.PasswordReset;
import org.slcp.service.domain.RefreshToken;
import org.slcp.service.domain.User;
import org.slcp.service.domain.UserStatus;
import org.slcp.service.recovery.RecoveryContracts.ChangePasswordRequest;
import org.slcp.service.recovery.RecoveryContracts.NewPasswordRequest;
import org.slcp.service.recovery.RecoveryContracts.ResetPreview;
import org.slcp.service.recovery.RecoveryContracts.ResetRequest;
import org.slcp.service.recovery.RecoveryContracts.ResetResponse;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.LoginIdentifierRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperacion y cambio de contrasena.
 *
 * <p>Realiza SEC-06 a SEC-09. Tres decisiones gobiernan este flujo y conviene
 * enunciarlas, porque cada una ataja algo distinto.</p>
 *
 * <p><strong>El enlace no se devuelve nunca a quien lo pide.</strong> Quien pide
 * una recuperacion es anonimo. Devolverle el enlace, aunque fuese solo cuando
 * falla el correo, permitiria pedir la recuperacion de cualquier cuenta y
 * recibir la llave. Si el correo no sale, la operacion fracasa.</p>
 *
 * <p><strong>Emitir un enlace invalida los anteriores.</strong> De otro modo
 * quedarian varios validos a la vez y bastaria con que se filtrase cualquiera.</p>
 *
 * <p><strong>Cambiar la contrasena cierra todas las sesiones.</strong> Quien
 * recupera su acceso suele hacerlo porque sospecha que alguien mas lo tiene. Si
 * las sesiones abiertas sobrevivieran, el cambio no serviria de nada.</p>
 */
@Service
public class PasswordRecoveryService {

	/** Vida del enlace. Corta a proposito: quien lo pide lo usa en el momento. */
	private static final Duration PLAZO = Duration.ofMinutes(30);

	private final PasswordResetRepository resets;
	private final UserRepository users;
	private final LoginIdentifierRepository identifiers;
	private final RefreshTokenRepository refreshTokens;
	private final EventRecordRepository events;
	private final PasswordEncoder passwordEncoder;
	private final RecoveryMailer mailer;
	private final LoginThrottle throttle;
	private final Clock clock;

	public PasswordRecoveryService(PasswordResetRepository resets, UserRepository users,
			LoginIdentifierRepository identifiers, RefreshTokenRepository refreshTokens,
			EventRecordRepository events, PasswordEncoder passwordEncoder,
			RecoveryMailer mailer, LoginThrottle throttle, Clock clock) {
		this.resets = resets;
		this.users = users;
		this.identifiers = identifiers;
		this.refreshTokens = refreshTokens;
		this.events = events;
		this.passwordEncoder = passwordEncoder;
		this.mailer = mailer;
		this.throttle = throttle;
		this.clock = clock;
	}

	// =================================================================
	// Peticion
	// =================================================================

	/**
	 * Emite un enlace y lo envia por correo.
	 *
	 * <p>El limite de intentos se aplica aqui tambien, y no por ocultar si la
	 * cuenta existe --- eso ya lo revela el formulario de registro --- sino
	 * porque sin el, esta ruta permitiria enviar correos sin cuento a cualquier
	 * direccion registrada.</p>
	 */
	@Transactional
	public ResetResponse solicitar(ResetRequest peticion, String origen) {
		Instant momento = Instant.now(clock);

		Optional<LoginFailure> bloqueo = throttle.comprobar(peticion.identifier(), origen);
		if (bloqueo.isPresent()) {
			throw new RecoveryException(
					"Demasiadas solicitudes seguidas. Vuelva a intentarlo dentro de unos minutos");
		}

		Optional<User> encontrado = identifiers.resolver(peticion.identifier())
				.map(LoginIdentifier::getUserId)
				.flatMap(users::findById);

		if (encontrado.isEmpty()) {
			throttle.anotarFallo(peticion.identifier(), origen);
			registrar("PASSWORD_RESET_UNKNOWN", null, peticion.identifier(), origen, momento);
			throw new RecoveryException(
					"No existe ninguna cuenta con ese nombre de usuario ni con ese correo");
		}

		User persona = encontrado.get();

		if (persona.getStatus() != UserStatus.ACTIVE) {
			registrar("PASSWORD_RESET_INACTIVE", persona.getId(), persona.getUsername(), origen, momento);
			throw new RecoveryException(
					"Esa cuenta no esta operativa, de modo que cambiar su contrasena no serviria "
							+ "de nada. Consulte con el administrador de la plataforma");
		}

		// Los enlaces anteriores dejan de servir.
		resets.vigentesDe(persona.getId()).forEach(anterior -> anterior.revocar(momento));

		String token = TokenHasher.generar();
		resets.save(PasswordReset.emitir(TokenHasher.resumir(token), persona.getId(), origen,
				momento, momento.plus(PLAZO)));

		RecoveryMailer.Entrega entrega = mailer.enviar(persona.getEmail(), persona.getFullName(),
				token, (int) PLAZO.toMinutes());

		if (!entrega.enviado()) {
			// Sin correo no hay recuperacion posible: el enlace no puede entregarse
			// por ninguna otra via sin comprometer la cuenta.
			registrar("PASSWORD_RESET_UNDELIVERED", persona.getId(), persona.getUsername(),
					origen, momento);
			throw new RecoveryException(
					"No se pudo enviar el correo de recuperacion (" + entrega.detalle()
							+ "). Consulte con el administrador de la plataforma");
		}

		registrar("PASSWORD_RESET_REQUESTED", persona.getId(), persona.getUsername(), origen, momento);

		return new ResetResponse(true,
				"Le hemos enviado un correo a la direccion registrada. El enlace caduca en "
						+ PLAZO.toMinutes() + " minutos y solo puede usarse una vez.");
	}

	// =================================================================
	// Uso del enlace
	// =================================================================

	/** Estado del enlace, para que la pantalla sepa que mostrar. */
	@Transactional(readOnly = true)
	public ResetPreview describir(String token) {
		Instant momento = Instant.now(clock);

		Optional<PasswordReset> encontrado = resets.findByTokenHash(TokenHasher.resumir(token));
		if (encontrado.isEmpty()) {
			return new ResetPreview(false, "Ese enlace no corresponde a ninguna solicitud", "");
		}

		PasswordReset reset = encontrado.get();
		String motivo = reset.motivoDeRechazo(momento);
		if (!motivo.isEmpty()) {
			return new ResetPreview(false, motivo, "");
		}

		String usuario = users.findById(reset.getUserId()).map(User::getUsername).orElse("");
		return new ResetPreview(true, "", usuario);
	}

	/** Fija la contrasena nueva y cierra todas las sesiones abiertas. */
	@Transactional
	public void restablecer(String token, NewPasswordRequest peticion, String origen) {
		Instant momento = Instant.now(clock);

		PasswordReset reset = resets.findByTokenHash(TokenHasher.resumir(token))
				.orElseThrow(() -> new RecoveryException(
						"Ese enlace no corresponde a ninguna solicitud"));

		String motivo = reset.motivoDeRechazo(momento);
		if (!motivo.isEmpty()) {
			throw new RecoveryException(motivo);
		}

		User persona = users.findById(reset.getUserId())
				.orElseThrow(() -> new RecoveryException("La cuenta ya no existe"));

		persona.cambiarVerificador(passwordEncoder.encode(peticion.password()));
		reset.consumir(momento);
		cerrarSesiones(persona.getId(), momento);
		throttle.anotarAcierto(persona.getUsername());

		registrar("PASSWORD_RESET_COMPLETED", persona.getId(), persona.getUsername(), origen, momento);
	}

	// =================================================================
	// Cambio con sesion iniciada
	// =================================================================

	/**
	 * Cambia la contrasena de quien ya esta dentro.
	 *
	 * <p>Exige la contrasena actual aunque haya sesion: una sesion abierta y
	 * desatendida no debe bastar para apropiarse de la cuenta.</p>
	 */
	@Transactional
	public void cambiar(UUID userId, ChangePasswordRequest peticion, String origen) {
		Instant momento = Instant.now(clock);

		User persona = users.findById(userId)
				.orElseThrow(() -> new RecoveryException("Cuenta no encontrada"));

		if (persona.getPasswordVerifier() == null
				|| !passwordEncoder.matches(peticion.currentPassword(), persona.getPasswordVerifier())) {
			registrar("PASSWORD_CHANGE_DENIED", userId, persona.getUsername(), origen, momento);
			throw new RecoveryException("La contrasena actual no es correcta");
		}

		if (passwordEncoder.matches(peticion.newPassword(), persona.getPasswordVerifier())) {
			throw new RecoveryException("La contrasena nueva es la misma que la actual");
		}

		persona.cambiarVerificador(passwordEncoder.encode(peticion.newPassword()));
		cerrarSesiones(userId, momento);

		registrar("PASSWORD_CHANGED", userId, persona.getUsername(), origen, momento);
	}

	// =================================================================

	/**
	 * Revoca todas las sesiones abiertas de la cuenta.
	 *
	 * <p>Es lo que da sentido al cambio. Quien recupera su acceso suele hacerlo
	 * porque sospecha que alguien mas lo tiene; dejar vivas las sesiones abiertas
	 * mantendria dentro a esa persona hasta que su token caducase.</p>
	 */
	private void cerrarSesiones(UUID userId, Instant momento) {
		for (RefreshToken token : refreshTokens.findByUserIdAndRevokedAtIsNull(userId)) {
			token.revocar(momento, "PASSWORD_CHANGED");
		}
	}

	private void registrar(String tipo, UUID sujeto, String etiqueta, String origen, Instant momento) {
		events.save(EventRecord.de(tipo, "User",
				sujeto != null ? sujeto : new UUID(0L, 0L),
				sujeto, etiqueta, "origen=" + origen, momento));
	}
}
