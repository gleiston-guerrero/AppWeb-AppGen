package org.slcp.service.registration;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

	/**
	 * Registra la solicitud.
	 *
	 * <p>Devuelve 201 con la cabecera {@code Location} apuntando al recurso
	 * creado. Sin ella, quien consume la respuesta no sabe donde quedo lo que
	 * acaba de crear y ha de deducirlo, que es justo lo que la cabecera evita.</p>
	 */
	@PostMapping
	public ResponseEntity<RegistrationResponse> solicitar(
			@Valid @RequestBody RegistrationRequest peticion) {

		RegistrationResponse respuesta = service.solicitar(peticion);
		URI ubicacion = URI.create("/api/v1/registrations/" + respuesta.readableId());
		return ResponseEntity.created(ubicacion).body(respuesta);
	}
}
