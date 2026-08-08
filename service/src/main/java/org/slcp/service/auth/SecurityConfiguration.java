package org.slcp.service.auth;

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
@EnableConfigurationProperties(SessionProperties.class)
public class SecurityConfiguration {

	private final SessionProperties propiedades;

	public SecurityConfiguration(SessionProperties propiedades) {
		this.propiedades = propiedades;
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
			.csrf(csrf -> csrf
					.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
					.ignoringRequestMatchers("/api/v1/auth/sessions"))
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(rutas -> rutas
					.requestMatchers(HttpMethod.GET, "/api/v1/platform-info").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/registrations").permitAll()
					.requestMatchers("/api/v1/auth/**").permitAll()
					.requestMatchers("/actuator/health").permitAll()
					.anyRequest().authenticated())
			.oauth2ResourceServer(oauth -> oauth
					.jwt(Customizer.withDefaults())
					.bearerTokenResolver(new CookieBearerTokenResolver(propiedades.accessCookieName())))
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());

		return http.build();
	}
}
