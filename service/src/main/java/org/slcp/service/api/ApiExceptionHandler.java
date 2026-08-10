package org.slcp.service.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slcp.service.administration.InvalidDecisionException;
import org.slcp.service.administration.RegistrationNotFoundException;
import org.slcp.service.auth.AuthenticationFailedException;
import org.slcp.service.auth.LoginFailure;
import org.slcp.service.invitations.InvitationException;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.recovery.RecoveryException;
import org.slcp.service.requirements.RequirementException;
import org.slcp.service.registration.RegistrationConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduccion de los fallos a respuestas.
 *
 * <p>Se emplea el codigo 422 con su nombre actual, contenido no procesable. La
 * RFC 9110 lo renombro y Spring 7 marco como obsoleta la denominacion anterior;
 * el codigo numerico no cambia.</p>
 *
 * <p>Cada respuesta dice que ocurrio y, cuando procede, que hacer. Un mensaje
 * generico no protege nada que otra ruta no revele ya, y en cambio deja a quien
 * se equivoca sin saber que corregir.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	/** Cuerpo de error, uniforme para toda la API. */
	public record ApiError(Instant timestamp, int status, String code, String message,
			String path, Map<String, String> fields) {
	}

	@ExceptionHandler(AuthenticationFailedException.class)
	public ResponseEntity<ApiError> acceso(AuthenticationFailedException fallo,
			HttpServletRequest peticion) {
		HttpStatus estado = fallo.getMotivo() == LoginFailure.TOO_MANY_ATTEMPTS
				? HttpStatus.TOO_MANY_REQUESTS
				: HttpStatus.UNAUTHORIZED;

		return ResponseEntity.status(estado).body(new ApiError(Instant.now(), estado.value(),
				fallo.getMotivo().name(), fallo.getMessage(), peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(RegistrationConflictException.class)
	public ResponseEntity<ApiError> conflicto(RegistrationConflictException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(Instant.now(),
				HttpStatus.CONFLICT.value(), "REGISTRATION_CONFLICT", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(InvitationException.class)
	public ResponseEntity<ApiError> invitacion(InvitationException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "INVITATION", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(RecoveryException.class)
	public ResponseEntity<ApiError> recuperacion(RecoveryException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "PASSWORD_RECOVERY", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(RequirementException.class)
	public ResponseEntity<ApiError> requisito(RequirementException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "REQUIREMENT", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiError> estado(IllegalStateException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "INVALID_STATE", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(ProjectAccessException.class)
	public ResponseEntity<ApiError> proyecto(ProjectAccessException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(Instant.now(),
				HttpStatus.FORBIDDEN.value(), "PROJECT_ACCESS", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(RegistrationNotFoundException.class)
	public ResponseEntity<ApiError> noEncontrado(RegistrationNotFoundException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(Instant.now(),
				HttpStatus.NOT_FOUND.value(), "REGISTRATION_NOT_FOUND", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(InvalidDecisionException.class)
	public ResponseEntity<ApiError> decisionInvalida(InvalidDecisionException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "INVALID_DECISION", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	/**
	 * Errores de validacion, campo por campo.
	 *
	 * <p>Se devuelven todos a la vez y no el primero: obligar a corregir de uno en
	 * uno convierte un formulario de tres campos en tres viajes.</p>
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException fallo,
			HttpServletRequest peticion) {
		Map<String, String> campos = new LinkedHashMap<>();
		fallo.getBindingResult().getFieldErrors()
				.forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));

		return ResponseEntity.badRequest().body(new ApiError(Instant.now(),
				HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
				"Revise los datos indicados", peticion.getRequestURI(), campos));
	}
}
