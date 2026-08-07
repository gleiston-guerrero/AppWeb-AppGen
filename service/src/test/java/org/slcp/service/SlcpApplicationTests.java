package org.slcp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprobacion de humo: el contexto de la aplicacion se levanta.
 *
 * <p>Es la unica prueba que depende de la infraestructura de Spring. Si falla,
 * el problema esta en la configuracion o en las dependencias, no en la logica.</p>
 */
@SpringBootTest
class SlcpApplicationTests {

	@Test
	@DisplayName("El contexto de la aplicacion arranca")
	void contextLoads() {
		// Sin aserciones: el fallo se manifiesta como excepcion al levantar el contexto.
	}
}
