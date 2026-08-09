package org.slcp.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.slcp.service.domain.User;
import org.slcp.service.registration.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apertura, renovacion, consulta y cierre de sesion.
 *
 * <p>La ruta trata la sesion como un recurso: crearla es iniciar sesion y
 * borrarla es cerrarla. Ningun token aparece en el cuerpo de la respuesta: van
 * en cookies que el codigo de la pagina no puede leer, conforme a SEC-01.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
public class AuthenticationController {

	private final AuthenticationService service;
	private final UserRepository users;
	private final JwtEncoder jwtEncoder;
	private final SessionProperties propiedades;

	public AuthenticationController(AuthenticationService service, UserRepository users,
			JwtEncoder jwtEncoder, SessionProperties propiedades) {
		this.service = service;
		this.users = users;
		this.jwtEncoder = jwtEncoder;
		this.propiedades = propiedades;
	}

	@PostMapping
	public ResponseEntity<SessionResponse> iniciar(@Valid @RequestBody LoginRequest peticion,
			HttpServletRequest http) {
		return responder(service.iniciar(peticion, origen(http)));
	}

	/**
	 * Renueva la sesion sustituyendo la vigente.
	 *
	 * <p>Se emplea PUT sobre el recurso y no POST sobre una ruta terminada en un
	 * verbo: renovar es sustituir la sesion actual por otra, que es exactamente lo
	 * que PUT significa. Una ruta con verbo seria una llamada a procedimiento
	 * disfrazada de recurso.</p>
	 */
	@PutMapping("/current")
	public ResponseEntity<SessionResponse> renovar(
			@CookieValue(name = "${slcp.session.refresh-cookie-name}", required = false) String refresh,
			HttpServletRequest http) {
		if (refresh == null) {
			throw new AuthenticationFailedException(LoginFailure.UNKNOWN_IDENTIFIER);
		}
		return responder(service.renovar(refresh, origen(http)));
	}

	/**
	 * Cierra la sesion vigente.
	 *
	 * <p>El borrado recae sobre el elemento y no sobre la coleccion. Un
	 * {@code DELETE} sobre la coleccion pedirla, literalmente, borrar todas las
	 * sesiones de todo el mundo.</p>
	 */
	@DeleteMapping("/current")
	public ResponseEntity<Void> cerrar(
			@CookieValue(name = "${slcp.session.refresh-cookie-name}", required = false) String refresh,
			HttpServletRequest http) {
		service.cerrar(refresh, origen(http));

		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.add(HttpHeaders.SET_COOKIE, SessionCookies
				.borrar(propiedades.accessCookieName(), "/api", propiedades.cookieSecure()).toString());
		cabeceras.add(HttpHeaders.SET_COOKIE, SessionCookies
				.borrar(propiedades.refreshCookieName(), "/api/v1/auth", propiedades.cookieSecure()).toString());
		return ResponseEntity.noContent().headers(cabeceras).build();
	}

	/** Quien esta atendiendose ahora mismo. La interfaz la usa al cargar. */
	@GetMapping("/current")
	public SessionResponse actual(@AuthenticationPrincipal Jwt jwt) {
		User usuario = users.findById(java.util.UUID.fromString(jwt.getSubject()))
				.orElseThrow(() -> new AuthenticationFailedException(LoginFailure.UNKNOWN_IDENTIFIER));
		return new SessionResponse(usuario.getId(), usuario.getReadableId(), usuario.getUsername(),
				usuario.getFullName(), usuario.getPlatformRole().name(),
				usuario.isMustChangePassword(), jwt.getExpiresAt());
	}

	private ResponseEntity<SessionResponse> responder(AuthenticationService.Sesion sesion) {
		String acceso = firmar(sesion.usuario(), sesion.expiraEn());

		ResponseCookie cookieAcceso = SessionCookies.acceso(propiedades.accessCookieName(),
				acceso, propiedades.accessTtl(), propiedades.cookieSecure());
		ResponseCookie cookieRenovacion = SessionCookies.renovacion(propiedades.refreshCookieName(),
				sesion.refreshToken(), propiedades.refreshTtl(), propiedades.cookieSecure());

		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.add(HttpHeaders.SET_COOKIE, cookieAcceso.toString());
		cabeceras.add(HttpHeaders.SET_COOKIE, cookieRenovacion.toString());

		User u = sesion.usuario();
		return ResponseEntity.ok().headers(cabeceras)
				.body(new SessionResponse(u.getId(), u.getReadableId(), u.getUsername(),
						u.getFullName(), u.getPlatformRole().name(),
						u.isMustChangePassword(), sesion.expiraEn()));
	}

	/**
	 * Contenido del token: el identificador interno y nada mas.
	 *
	 * <p>SEC-04 excluye los roles a proposito. ROL-01 los resuelve por proyecto, de
	 * modo que incrustarlos aqui daria una respuesta que queda obsoleta en cuanto
	 * el facilitador cambia una asignacion.</p>
	 */
	private String firmar(User usuario, Instant expira) {
		JwtClaimsSet reclamaciones = JwtClaimsSet.builder()
				.issuer("slcp")
				.subject(usuario.getId().toString())
				.issuedAt(Instant.now())
				.expiresAt(expira)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),
				reclamaciones)).getTokenValue();
	}

	private String origen(HttpServletRequest http) {
		String reenviado = http.getHeader("X-Forwarded-For");
		return reenviado != null ? reenviado : String.valueOf(http.getRemoteAddr());
	}

}
