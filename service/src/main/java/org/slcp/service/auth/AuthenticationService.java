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
	private final Clock clock;

	public AuthenticationService(UserRepository users, LoginIdentifierRepository identifiers,
			RefreshTokenRepository refreshTokens, EventRecordRepository events,
			PasswordEncoder passwordEncoder, SessionProperties propiedades, Clock clock) {
		this.users = users;
		this.identifiers = identifiers;
		this.refreshTokens = refreshTokens;
		this.events = events;
		this.passwordEncoder = passwordEncoder;
		this.propiedades = propiedades;
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

		Optional<User> encontrado = identifiers.resolver(peticion.identifier())
				.map(LoginIdentifier::getUserId)
				.flatMap(users::findById);

		// La contrasena se verifica incluso cuando no hay cuenta, contra un
		// verificador ficticio, para que el tiempo de respuesta no revele si el
		// identificador existe.
		String verificador = encontrado.map(User::getPasswordVerifier)
				.orElse("$2a$12$0000000000000000000000000000000000000000000000000000u");
		boolean coincide = passwordEncoder.matches(peticion.password(), verificador);

		if (encontrado.isEmpty() || !coincide || !encontrado.get().puedeIniciarSesion()) {
			registrar("SESSION_DENIED", encontrado.map(User::getId).orElse(null),
					peticion.identifier(), origen, momento);
			throw new AuthenticationFailedException();
		}

		User usuario = encontrado.get();
		String refresh = TokenHasher.generar();
		Instant caducidadRefresh = momento.plus(propiedades.refreshTtl());
		refreshTokens.save(RefreshToken.emitir(TokenHasher.resumir(refresh), usuario.getId(),
				momento, caducidadRefresh));

		registrar("SESSION_OPENED", usuario.getId(), usuario.getUsername(), origen, momento);

		return new Sesion(usuario, refresh, momento.plus(propiedades.accessTtl()));
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
				.orElseThrow(AuthenticationFailedException::new);

		User usuario = users.findById(vigente.getUserId())
				.filter(User::puedeIniciarSesion)
				.orElseThrow(AuthenticationFailedException::new);

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
