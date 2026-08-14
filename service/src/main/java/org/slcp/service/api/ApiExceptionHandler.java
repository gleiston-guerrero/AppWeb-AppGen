package org.slcp.service.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slcp.service.administration.InvalidDecisionException;
import org.slcp.service.administration.RegistrationNotFoundException;
import org.slcp.service.auth.AuthenticationFailedException;
import org.slcp.service.auth.LoginFailure;
import org.slcp.service.invitations.InvitationException;
import org.slcp.service.deliverables.DeliverableException;
import org.slcp.service.generation.GenerationException;
import org.slcp.service.planning.PlanningException;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.recovery.RecoveryException;
import org.slcp.service.requirements.RequirementException;
import org.slcp.service.registration.RegistrationConflictException;
import org.springframework.dao.DataIntegrityViolationException;
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

	/**
	 * Explica los choques contra las reglas de la base de datos.
	 *
	 * <p>Sin esto, una restriccion violada llega como error 500 y quien lo recibe
	 * solo sabe que algo fallo. La base impone reglas de negocio --- segregacion
	 * de roles, unicidad de identificadores, integridad de enlaces --- y cuando
	 * una se incumple, el cliente merece saber cual y que hacer.</p>
	 *
	 * <p>Se responde 409, que es lo que corresponde: la peticion es correcta y
	 * choca con el estado actual del recurso.</p>
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> integridad(DataIntegrityViolationException fallo,
			HttpServletRequest peticion) {

		String causa = fallo.getMostSpecificCause().getMessage();
		String texto = causa == null ? "" : causa.toLowerCase(Locale.ROOT);

		String explicacion = explicarIntegridad(texto);

		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(Instant.now(),
				HttpStatus.CONFLICT.value(), "CONFLICT", explicacion,
				peticion.getRequestURI(), Map.of()));
	}

	/**
	 * Traduce el mensaje de la base a algo accionable.
	 *
	 * <p>Se reconocen las restricciones por su nombre, que es estable, y no por el
	 * texto del error, que depende del idioma con que este instalado el gestor.</p>
	 */
	private String explicarIntegridad(String causa) {
		if (causa.contains("uq_deliverables_readable")) {
			return "Ya existe un entregable con ese identificador en el proyecto. "
					+ "Vuelva a intentarlo: la plataforma asignara el siguiente libre";
		}
		if (causa.contains("uq_requirements_readable")) {
			return "Ya existe un requisito con ese identificador en el proyecto. "
					+ "Vuelva a intentarlo: la plataforma asignara el siguiente libre";
		}
		if (causa.contains("uq_memberships_rol_unico")) {
			return "Esa persona ya tiene ese rol en el proyecto";
		}
		if (causa.contains("rol-06")) {
			return "ROL-06: el propietario del producto no puede acumular otro rol en el mismo "
					+ "proyecto";
		}
		if (causa.contains("ver-03")) {
			return "Solo se generan pruebas y diagramas de requisitos aprobados";
		}
		if (causa.contains("ck_artifacts_review")) {
			return "Un artefacto aceptado ha de constar de quien lo acepto";
		}
		if (causa.contains("wbs-09")) {
			return "Solo se asignan tareas a miembros del equipo del proyecto";
		}
		if (causa.contains("uq_components_readable") || causa.contains("uq_tasks_readable")
				|| causa.contains("uq_activities_readable") || causa.contains("uq_resources_readable")) {
			return "Ya existe un elemento con ese identificador en el proyecto. Vuelva a intentarlo";
		}
		if (causa.contains("ck_tasks_effort") || causa.contains("ck_activities_effort")) {
			return "El esfuerzo previsto ha de ser mayor que cero";
		}
		if (causa.contains("ck_time_hours")) {
			return "Las horas dedicadas han de estar entre cero y veinticuatro";
		}
		if (causa.contains("wbs-07")) {
			return "Solo se enlaza trabajo a requisitos aprobados";
		}
		if (causa.contains("wbs-08") || causa.contains("rqm-20")) {
			return "Ese elemento fue decidido y no puede eliminarse. Anulelo o retirelo, que "
					+ "conserva su historia";
		}
		if (causa.contains("prj-02")) {
			return "El proyecto tiene requisitos y no puede eliminarse. Retirelo del servicio";
		}
		if (causa.contains("uq_users_email") || causa.contains("uq_login_identifiers")) {
			return "Ya existe una cuenta con ese correo o identificador";
		}
		if (causa.contains("foreign key") || causa.contains("llave foranea")) {
			return "La operacion dejaria datos apuntando a algo que no existe";
		}
		return "La operacion choca con una regla del sistema: " + causa;
	}

	@ExceptionHandler(GenerationException.class)
	public ResponseEntity<ApiError> generacion(GenerationException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "GENERATION", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(PlanningException.class)
	public ResponseEntity<ApiError> planificacion(PlanningException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "PLANNING", fallo.getMessage(),
				peticion.getRequestURI(), Map.of()));
	}

	@ExceptionHandler(DeliverableException.class)
	public ResponseEntity<ApiError> entregable(DeliverableException fallo,
			HttpServletRequest peticion) {
		return ResponseEntity.unprocessableContent().body(new ApiError(Instant.now(),
				HttpStatus.UNPROCESSABLE_CONTENT.value(), "DELIVERABLE", fallo.getMessage(),
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
