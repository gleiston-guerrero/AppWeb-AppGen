package org.slcp.service.projects;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.ProjectRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proyectos y equipo.
 *
 * <p>La creacion exige la atribucion de facilitador, que se obtiene por
 * autorregistro aprobado. Las demas operaciones se autorizan por membresia.</p>
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

	private final ProjectService service;

	public ProjectController(ProjectService service) {
		this.service = service;
	}

	@GetMapping
	public List<ProjectView> mios(@AuthenticationPrincipal Jwt jwt) {
		return service.mios(UUID.fromString(jwt.getSubject()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectView crear(@Valid @RequestBody ProjectRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.crear(peticion, UUID.fromString(jwt.getSubject()));
	}

	@PutMapping("/{readableId}")
	public ProjectView editar(@PathVariable String readableId,
			@Valid @RequestBody ProjectRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.editar(readableId, peticion, UUID.fromString(jwt.getSubject()));
	}

	/**
	 * Retira el proyecto del servicio o lo devuelve a el.
	 *
	 * <p>El estado es un recurso del proyecto y se sustituye con PUT, conforme a
	 * API-01.</p>
	 */
	@PutMapping("/{readableId}/status")
	public ProjectView cambiarEstado(@PathVariable String readableId,
			@RequestParam boolean active, @AuthenticationPrincipal Jwt jwt) {
		return service.cambiarEstado(readableId, active, UUID.fromString(jwt.getSubject()));
	}

	@DeleteMapping("/{readableId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable String readableId, @AuthenticationPrincipal Jwt jwt) {
		service.eliminar(readableId, UUID.fromString(jwt.getSubject()));
	}

	@PutMapping("/{readableId}/team/{username}")
	public MemberView cambiarRol(@PathVariable String readableId, @PathVariable String username,
			@RequestParam ProjectRole role, @AuthenticationPrincipal Jwt jwt) {
		return service.cambiarRol(readableId, username, role, UUID.fromString(jwt.getSubject()));
	}

	@DeleteMapping("/{readableId}/team/{username}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void retirarDelEquipo(@PathVariable String readableId, @PathVariable String username,
			@AuthenticationPrincipal Jwt jwt) {
		service.retirarDelEquipo(readableId, username, UUID.fromString(jwt.getSubject()));
	}

	@GetMapping("/{readableId}/team")
	public List<MemberView> equipo(@PathVariable String readableId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.equipo(readableId, UUID.fromString(jwt.getSubject()));
	}

	@PostMapping("/{readableId}/team")
	@ResponseStatus(HttpStatus.CREATED)
	public MemberView incorporar(@PathVariable String readableId,
			@Valid @RequestBody MemberRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.incorporar(readableId, peticion, UUID.fromString(jwt.getSubject()));
	}
}
