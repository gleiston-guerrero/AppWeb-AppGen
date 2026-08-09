package org.slcp.service.administration;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.User;
import org.slcp.service.registration.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de la plataforma.
 *
 * <p>Toda esta ruta exige el rol global de administrador, impuesto en la
 * configuracion de seguridad. La aprobacion se modela como recurso propio de la
 * solicitud y se sustituye con PUT, conforme a API-01.</p>
 */
@RestController
@RequestMapping("/api/v1/administration")
public class AdministrationController {

	private final RegistrationApprovalService service;
	private final UserRepository users;

	public AdministrationController(RegistrationApprovalService service, UserRepository users) {
		this.service = service;
		this.users = users;
	}

	@GetMapping("/registrations/pending")
	public List<PendingRegistration> pendientes() {
		return service.pendientes();
	}

	@PutMapping("/registrations/{readableId}/approval")
	public ApprovalResult decidir(@PathVariable String readableId,
			@Valid @RequestBody ApprovalDecision decision,
			@AuthenticationPrincipal Jwt jwt) {

		UUID actorId = UUID.fromString(jwt.getSubject());
		String etiqueta = users.findById(actorId).map(User::getUsername).orElse("desconocido");

		return service.decidir(readableId, decision, actorId, etiqueta);
	}
}
