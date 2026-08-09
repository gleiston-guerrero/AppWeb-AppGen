package org.slcp.service.auth;

import java.time.Clock;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Configuracion de seguridad.
 *
 * <p>Realiza SEC-01 a SEC-05. La sesion es sin estado en el servidor: el token
 * viaja en cookie no legible por script, y la autorizacion se resuelve en cada
 * peticion.</p>
 */
@Configuration
@EnableConfigurationProperties({ SessionProperties.class, org.slcp.service.invitations.MailProperties.class })
public class SecurityConfiguration {

	private final SessionProperties propiedades;
	private final UserAuthoritiesConverter atribuciones;

	public SecurityConfiguration(SessionProperties propiedades,
			UserAuthoritiesConverter atribuciones) {
		this.propiedades = propiedades;
		this.atribuciones = atribuciones;
	}

	/**
	 * Verificador de contrasenas.
	 *
	 * <p>Se emplea bcrypt con factor de coste 12. NIST SP 800-63B-4 admite varias
	 * funciones y prefiere las que consumen memoria, como Argon2id; se adopta
	 * bcrypt en este incremento por no introducir dependencias nuevas mientras la
	 * construccion se estabiliza, y el cambio posterior a Argon2id afecta a esta
	 * unica linea gracias al prefijo que bcrypt deja en el verificador.</p>
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	/**
	 * Limitador de intentos de acceso.
	 *
	 * <p>La ventana de quince minutos es un punto de partida: suficiente para
	 * frenar un ataque automatizado y lo bastante corta para que quien se equivoca
	 * de buena fe no quede fuera media jornada.</p>
	 */
	@Bean
	public LoginThrottle loginThrottle(Clock clock) {
		return new LoginThrottle(Duration.ofMinutes(15), clock);
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(clave()));
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withSecretKey(clave())
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	private SecretKeySpec clave() {
		return new SecretKeySpec(propiedades.secret().getBytes(), "HmacSHA256");
	}

	@Bean
	public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
		http
			// El token de proteccion contra falsificacion si debe ser legible por el
			// codigo de la pagina: es lo que permite reenviarlo en una cabecera. No
			// confundir con el token de sesion, que nunca lo es.
			//
			// Quedan exentas UNICAMENTE las rutas anteriores a la sesion. La
			// falsificacion de peticion consiste en aprovechar la sesion abierta de
			// una victima; donde todavia no hay sesion no hay nada que aprovechar, y
			// exigir el token ahi solo impide el uso legitimo.
			//
			// La renovacion y el cierre NO estan exentos, aunque compartan prefijo:
			// ambos emplean la cookie de sesion, y un cierre forzado desde otro sitio
			// es un ataque real aunque su dano sea moderado.
			.csrf(csrf -> csrf
					.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
					// Ambas rutas admiten unicamente POST, de modo que nombrarlas por
					// cadena equivale a acotarlas por metodo y evita depender de una
					// API de comparadores que Spring Security esta reemplazando.
					.ignoringRequestMatchers(
							"/api/v1/registrations",
							"/api/v1/auth/sessions",
							"/api/v1/invitations/*/completion"))
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(rutas -> rutas
					.requestMatchers(HttpMethod.GET, "/api/v1/platform-info").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/registrations").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/auth/sessions").permitAll()
					.requestMatchers("/actuator/health").permitAll()
					// El enlace de invitacion es publico: quien lo abre aun no tiene
					// cuenta. La autorizacion la aporta el propio enlace, que es
					// aleatorio, de un solo uso y ligado a un correo y un proyecto.
					.requestMatchers(HttpMethod.GET, "/api/v1/invitations/*").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/invitations/*/completion").permitAll()
					// La administracion exige el rol global. Ocultar la opcion en la
					// interfaz no basta: SEC-05 exige que la restriccion este impuesta
					// tambien aqui, donde una peticion construida a mano tropieza igual.
					.requestMatchers("/api/v1/administration/**").hasRole("ADMINISTRATOR")
					.requestMatchers(HttpMethod.GET, "/api/v1/registrations/**").hasRole("ADMINISTRATOR")
					// Crear proyectos exige la atribucion que concede el autorregistro.
					// El resto de operaciones sobre un proyecto se autorizan por
					// membresia, que el servicio comprueba: el rol no basta.
					.requestMatchers(HttpMethod.POST, "/api/v1/projects").hasRole("FACILITATOR")
					.anyRequest().authenticated())
			.oauth2ResourceServer(oauth -> oauth
					.jwt(jwt -> jwt.jwtAuthenticationConverter(atribuciones))
					.bearerTokenResolver(new CookieBearerTokenResolver(propiedades.accessCookieName())))
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());

		return http.build();
	}
}
