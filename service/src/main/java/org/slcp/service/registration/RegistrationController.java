package org.slcp.service.registration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de solicitudes de registro.
 *
 * <p>La ruta emplea kebab-case en plural conforme a NAM. El recurso es publico
 * porque quien se registra no tiene aun cuenta con la que autenticarse.</p>
 */
@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

	private final RegistrationService service;

	public RegistrationController(RegistrationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegistrationResponse solicitar(@Valid @RequestBody RegistrationRequest peticion) {
		return service.solicitar(peticion);
	}
}
