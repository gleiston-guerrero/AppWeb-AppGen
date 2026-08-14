package org.slcp.service.generation;

import java.util.List;
import java.util.UUID;
import org.slcp.service.generation.SpecificationService.GenerateRequest;
import org.slcp.service.generation.SpecificationService.SpecificationRequest;
import org.slcp.service.generation.SpecificationService.SpecificationView;
import org.slcp.service.generation.SpecificationService.SpecificationsView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Casos de uso expandidos e historias de usuario. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/specifications")
public class SpecificationController {

	private final SpecificationService service;

	public SpecificationController(SpecificationService service) {
		this.service = service;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	@GetMapping
	public SpecificationsView consultar(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.consultar(projectId, quien(jwt));
	}

	/** Genera con el modelo configurado. Sin el, se rechaza y se dice por que. */
	@PostMapping("/generate")
	@ResponseStatus(HttpStatus.CREATED)
	public List<SpecificationView> generar(@PathVariable String projectId,
			@RequestBody GenerateRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.generar(projectId, peticion, quien(jwt));
	}

	/** Alta escrita desde cero, sin modelo. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SpecificationView crear(@PathVariable String projectId,
			@RequestBody SpecificationRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.crear(projectId, peticion, quien(jwt));
	}

	@PutMapping("/{specId}")
	public SpecificationView editar(@PathVariable String projectId, @PathVariable String specId,
			@RequestBody SpecificationRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.editar(projectId, specId, peticion, quien(jwt));
	}

	@PutMapping("/{specId}/acceptance")
	public SpecificationView aceptar(@PathVariable String projectId, @PathVariable String specId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.aceptar(projectId, specId, quien(jwt));
	}

	@PutMapping("/{specId}/discard")
	public SpecificationView descartar(@PathVariable String projectId, @PathVariable String specId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.descartar(projectId, specId, quien(jwt));
	}

	/** Retira la regla base para poder regenerar. */
	@DeleteMapping("/{specId}/baseline")
	public SpecificationView retirarReglaBase(@PathVariable String projectId,
			@PathVariable String specId, @AuthenticationPrincipal Jwt jwt) {
		return service.retirarReglaBase(projectId, specId, quien(jwt));
	}

	@DeleteMapping("/{specId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable String projectId, @PathVariable String specId,
			@AuthenticationPrincipal Jwt jwt) {
		service.eliminar(projectId, specId, quien(jwt));
	}
}
