package org.slcp.service.api;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de acceso publico a la informacion de la plataforma.
 *
 * <p>Realiza FUN-01 y FUN-02. No exige sesion iniciada y no expone dato alguno
 * de ningun proyecto, conforme al criterio de verificacion de FUN-01.</p>
 *
 * <p>La ruta sigue la convencion de NAM: kebab-case en plural para los segmentos
 * de servicio.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class PlatformInfoController {

	private final String version;

	public PlatformInfoController(@Value("${slcp.version:0.1.0-SNAPSHOT}") String version) {
		this.version = version;
	}

	/**
	 * Informacion publica de la plataforma.
	 *
	 * <p>El contenido no cambia entre despliegues, de modo que se declara
	 * cacheable. Sin esa declaracion, cada visita vuelve a pedirlo entero aunque
	 * nada haya cambiado, y el navegador no tiene forma de saberlo.</p>
	 */
	@GetMapping("/platform-info")
	public ResponseEntity<PlatformInfo> platformInfo() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
				.body(PlatformInfo.current(version));
	}
}
