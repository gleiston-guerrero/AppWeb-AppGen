package org.slcp.service.deliverables;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slcp.service.deliverables.DeliverableContracts.DeliverableRequest;
import org.slcp.service.deliverables.DeliverableContracts.DeliverableView;
import org.slcp.service.deliverables.DeliverableContracts.LinkableRequirement;
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

/** Entregables de un proyecto. Cuelgan de el porque a el pertenecen. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/deliverables")
public class DeliverableController {

	private final DeliverableService service;

	public DeliverableController(DeliverableService service) {
		this.service = service;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	@GetMapping
	public List<DeliverableView> listar(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.listar(projectId, quien(jwt));
	}

	/** Requisitos aprobados que pueden enlazarse a un entregable. */
	@GetMapping("/linkable-requirements")
	public List<LinkableRequirement> enlazables(@PathVariable String projectId,
			@RequestParam(required = false) String deliverable, @AuthenticationPrincipal Jwt jwt) {
		return service.enlazables(projectId, deliverable, quien(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeliverableView crear(@PathVariable String projectId,
			@Valid @RequestBody DeliverableRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.crear(projectId, peticion, quien(jwt));
	}

	@PutMapping("/{readableId}")
	public DeliverableView editar(@PathVariable String projectId, @PathVariable String readableId,
			@Valid @RequestBody DeliverableRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.editar(projectId, readableId, peticion, quien(jwt));
	}

	@DeleteMapping("/{readableId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable String projectId, @PathVariable String readableId,
			@AuthenticationPrincipal Jwt jwt) {
		service.eliminar(projectId, readableId, quien(jwt));
	}

	@PutMapping("/{readableId}/status")
	public DeliverableView transitar(@PathVariable String projectId,
			@PathVariable String readableId, @RequestParam String to,
			@AuthenticationPrincipal Jwt jwt) {
		return service.transitar(projectId, readableId, to, quien(jwt));
	}
}
