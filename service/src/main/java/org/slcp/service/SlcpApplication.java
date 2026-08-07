package org.slcp.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque del servicio SLCP.
 *
 * <p>Esta primera version no depende de ningun servicio externo. La persistencia
 * se incorpora en el incremento siguiente, una vez comprobado que el servicio
 * arranca por si solo.</p>
 */
@SpringBootApplication
public class SlcpApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlcpApplication.class, args);
	}
}
