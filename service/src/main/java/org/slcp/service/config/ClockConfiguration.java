package org.slcp.service.config;

import java.time.Clock;
import org.slcp.service.registration.EmailDomainChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicacion.
 *
 * <p>Se declara como componente y no se invoca {@code Instant.now()} de forma
 * directa, para que el tiempo sea sustituible en las pruebas. Sin esto, ninguna
 * prueba sobre marcas temporales seria determinista, y TRC-23 exige que lo que
 * se declara determinista lo sea.</p>
 */
@Configuration
public class ClockConfiguration {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * Comprobador del dominio de correo.
	 *
	 * <p>Tres segundos de espera: suficiente para una consulta de nombres normal y
	 * lo bastante corto para que una caida no deje colgado el formulario. Ante
	 * fallo no bloquea.</p>
	 */
	@Bean
	public EmailDomainChecker emailDomainChecker() {
		return new EmailDomainChecker(3000);
	}
}
