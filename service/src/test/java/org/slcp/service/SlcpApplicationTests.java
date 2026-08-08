package org.slcp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprobacion de humo: el contexto de la aplicacion se levanta.
 *
 * <p>Es la unica prueba que depende de la infraestructura de Spring. Si falla,
 * el problema esta en la configuracion o en las dependencias, no en la logica.
 * Emplea el perfil de prueba, que sustituye PostgreSQL por H2 en memoria de
 * forma provisional.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SlcpApplicationTests {

	@Test
	@DisplayName("El contexto de la aplicacion arranca")
	void contextLoads() {
		// Sin aserciones: el fallo se manifiesta como excepcion al levantar el contexto.
	}
}
