package org.slcp.service.invitations;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slcp.service.invitations.InvitationContracts.CompletionRequest;
import org.slcp.service.invitations.InvitationContracts.InvitationPreview;
import org.slcp.service.invitations.InvitationContracts.InviteRequest;
import org.slcp.service.invitations.InvitationContracts.InviteResult;
import org.slcp.service.invitations.InvitationContracts.JoinResult;
import org.slcp.service.invitations.InvitationContracts.PendingInvite;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitaciones del facilitador.
 *
 * <p>Cuelgan del proyecto porque a el pertenecen: una invitacion sin proyecto no
 * significa nada.</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/invitations")
public class InvitationController {

	private final InvitationService service;

	public InvitationController(InvitationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public InviteResult invitar(@PathVariable String projectId,
			@Valid @RequestBody InviteRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.invitar(projectId, peticion, UUID.fromString(jwt.getSubject()));
	}

	@GetMapping
	public List<PendingInvite> vigentes(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.vigentes(projectId, UUID.fromString(jwt.getSubject()));
	}

	@DeleteMapping("/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revocar(@PathVariable String projectId, @PathVariable String invitationId,
			@AuthenticationPrincipal Jwt jwt) {
		service.revocar(projectId, invitationId, UUID.fromString(jwt.getSubject()));
	}
}
