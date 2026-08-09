package org.slcp.service.invitations;

import java.util.List;
import java.util.UUID;
import org.slcp.service.invitations.InvitationContracts.JoinResult;
import org.slcp.service.invitations.InvitationContracts.PendingInvite;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitaciones dirigidas a quien ya tiene cuenta.
 *
 * <p>La aceptacion es un recurso de la invitacion y se sustituye con PUT,
 * conforme a API-01. Rechazarla es borrar ese recurso.</p>
 */
@RestController
@RequestMapping("/api/v1/my-invitations")
public class MyInvitationsController {

	private final InvitationService service;

	public MyInvitationsController(InvitationService service) {
		this.service = service;
	}

	@GetMapping
	public List<PendingInvite> mias(@AuthenticationPrincipal Jwt jwt) {
		return service.mias(UUID.fromString(jwt.getSubject()));
	}

	@PutMapping("/{invitationId}/acceptance")
	public JoinResult aceptar(@PathVariable String invitationId, @AuthenticationPrincipal Jwt jwt) {
		return service.aceptar(invitationId, UUID.fromString(jwt.getSubject()));
	}

	@DeleteMapping("/{invitationId}/acceptance")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void rechazar(@PathVariable String invitationId, @AuthenticationPrincipal Jwt jwt) {
		service.rechazar(invitationId, UUID.fromString(jwt.getSubject()));
	}
}
