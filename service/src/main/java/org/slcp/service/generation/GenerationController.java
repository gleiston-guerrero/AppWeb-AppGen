package org.slcp.service.generation;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slcp.service.generation.GenerationService.ArtifactView;
import org.slcp.service.generation.GenerationService.GenerateRequest;
import org.slcp.service.generation.GenerationService.GenerationView;
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

/** Pruebas y diagramas generados de los requisitos. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/generation")
public class GenerationController {

	private final GenerationService service;

	public GenerationController(GenerationService service) {
		this.service = service;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	/** Que puede generarse, que hay generado, y la cobertura. */
	@GetMapping
	public GenerationView consultar(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.consultar(projectId, quien(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public List<ArtifactView> generar(@PathVariable String projectId,
			@Valid @RequestBody GenerateRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.generar(projectId, peticion, quien(jwt));
	}

	/** Modificacion del contenido. Devuelve el artefacto a propuesto. */
	public record EditRequest(String title, String content) {
	}

	@PutMapping("/{artifactId}")
	public ArtifactView editar(@PathVariable String projectId, @PathVariable String artifactId,
			@RequestBody EditRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.editar(projectId, artifactId, peticion.title(), peticion.content(),
				quien(jwt));
	}

	@PutMapping("/{artifactId}/decision")
	public ArtifactView decidir(@PathVariable String projectId, @PathVariable String artifactId,
			@RequestParam boolean accept, @AuthenticationPrincipal Jwt jwt) {
		return service.decidir(projectId, artifactId, accept, quien(jwt));
	}

	/**
	 * El propietario del producto lo da por revisado.
	 *
	 * <p>Es distinto de aprobarlo, que corresponde al equipo. Por eso es una ruta
	 * propia y no un valor mas del estado.</p>
	 */
	@PutMapping("/{artifactId}/owner-review")
	public ArtifactView darPorRevisado(@PathVariable String projectId,
			@PathVariable String artifactId, @RequestParam boolean reviewed,
			@AuthenticationPrincipal Jwt jwt) {
		return service.darPorRevisado(projectId, artifactId, reviewed, quien(jwt));
	}

	@DeleteMapping("/{artifactId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable String projectId, @PathVariable String artifactId,
			@AuthenticationPrincipal Jwt jwt) {
		service.eliminar(projectId, artifactId, quien(jwt));
	}
}
