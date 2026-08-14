package org.slcp.service.generation;

import java.util.List;
import java.util.UUID;
import org.slcp.service.generation.AiSettingsService.ProbeResult;
import org.slcp.service.generation.AiSettingsService.ProviderView;
import org.slcp.service.generation.AiSettingsService.SettingsRequest;
import org.slcp.service.generation.AiSettingsService.SettingsView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuracion del servicio de IA generativa de un proyecto.
 *
 * <p>La credencial entra por el cuerpo de la peticion y nunca sale: ninguna
 * respuesta de este controlador la contiene. Llevarla en la direccion la dejaria
 * escrita en los registros del servidor y en el historial del navegador.</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/ai-settings")
public class AiSettingsController {

	private final AiSettingsService service;

	public AiSettingsController(AiSettingsService service) {
		this.service = service;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	/** Configuracion de todas las funciones, incluidas las no configuradas. */
	@GetMapping
	public List<SettingsView> consultar(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.consultar(projectId, quien(jwt));
	}

	/** Proveedores admitidos y sus valores habituales. */
	@GetMapping("/providers")
	public List<ProviderView> proveedores() {
		return service.proveedores();
	}

	@PutMapping("/{feature}")
	public SettingsView guardar(@PathVariable String projectId, @PathVariable String feature,
			@RequestBody SettingsRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.guardar(projectId, feature, peticion, quien(jwt));
	}

	@PutMapping("/{feature}/enabled")
	public SettingsView activar(@PathVariable String projectId, @PathVariable String feature,
			@RequestParam boolean active, @AuthenticationPrincipal Jwt jwt) {
		return service.activar(projectId, feature, active, quien(jwt));
	}

	@DeleteMapping("/{feature}/credential")
	@ResponseStatus(HttpStatus.OK)
	public SettingsView retirarCredencial(@PathVariable String projectId,
			@PathVariable String feature, @AuthenticationPrincipal Jwt jwt) {
		return service.retirarCredencial(projectId, feature, quien(jwt));
	}

	/** Comprueba que la configuracion sirve, antes de depender de ella. */
	@PostMapping("/{feature}/probe")
	public ProbeResult probar(@PathVariable String projectId, @PathVariable String feature,
			@AuthenticationPrincipal Jwt jwt) {
		return service.probar(projectId, feature, quien(jwt));
	}
}
