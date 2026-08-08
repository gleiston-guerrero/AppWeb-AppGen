package org.slcp.service.config;

import java.time.Clock;
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
}
