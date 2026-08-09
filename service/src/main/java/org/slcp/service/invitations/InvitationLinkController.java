package org.slcp.service.invitations;

import jakarta.validation.Valid;
import org.slcp.service.invitations.InvitationContracts.CompletionRequest;
import org.slcp.service.invitations.InvitationContracts.InvitationPreview;
import org.slcp.service.invitations.InvitationContracts.JoinResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uso del enlace de invitacion.
 *
 * <p>Es publico porque quien lo abre todavia no tiene cuenta con la que
 * autenticarse. La autorizacion la aporta el propio enlace: aleatorio, de un
 * solo uso, ligado a un correo y a un proyecto, y con caducidad.</p>
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationLinkController {

	private final InvitationService service;

	public InvitationLinkController(InvitationService service) {
		this.service = service;
	}

	/** Lo que se muestra antes de decidir. No revela nada del contenido del proyecto. */
	@GetMapping("/{token}")
	public InvitationPreview describir(@PathVariable String token) {
		return service.describir(token);
	}

	/** Completa el registro de quien no tenia cuenta. */
	@PostMapping("/{token}/completion")
	public JoinResult completar(@PathVariable String token,
			@Valid @RequestBody CompletionRequest peticion) {
		return service.completar(token, peticion);
	}
}
