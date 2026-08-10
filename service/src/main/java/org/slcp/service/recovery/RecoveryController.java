package org.slcp.service.recovery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slcp.service.recovery.RecoveryContracts.ChangePasswordRequest;
import org.slcp.service.recovery.RecoveryContracts.NewPasswordRequest;
import org.slcp.service.recovery.RecoveryContracts.ResetPreview;
import org.slcp.service.recovery.RecoveryContracts.ResetRequest;
import org.slcp.service.recovery.RecoveryContracts.ResetResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperacion de acceso.
 *
 * <p>Publica, porque quien la usa no puede autenticarse. La autorizacion la
 * aporta el enlace, que es aleatorio, de un solo uso, ligado a una cuenta y de
 * vida corta.</p>
 */
@RestController
@RequestMapping("/api/v1/password-resets")
public class RecoveryController {

	private final PasswordRecoveryService service;

	public RecoveryController(PasswordRecoveryService service) {
		this.service = service;
	}

	private String origen(HttpServletRequest http) {
		String reenviado = http.getHeader("X-Forwarded-For");
		return reenviado != null ? reenviado : String.valueOf(http.getRemoteAddr());
	}

	@PostMapping
	public ResetResponse solicitar(@Valid @RequestBody ResetRequest peticion,
			HttpServletRequest http) {
		return service.solicitar(peticion, origen(http));
	}

	@GetMapping("/{token}")
	public ResetPreview describir(@PathVariable String token) {
		return service.describir(token);
	}

	@PostMapping("/{token}/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restablecer(@PathVariable String token,
			@Valid @RequestBody NewPasswordRequest peticion, HttpServletRequest http) {
		service.restablecer(token, peticion, origen(http));
	}
}
