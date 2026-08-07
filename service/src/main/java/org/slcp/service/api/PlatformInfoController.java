package org.slcp.service.api;

import org.springframework.beans.factory.annotation.Value;
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

	@GetMapping("/platform-info")
	public PlatformInfo platformInfo() {
		return PlatformInfo.current(version);
	}
}
