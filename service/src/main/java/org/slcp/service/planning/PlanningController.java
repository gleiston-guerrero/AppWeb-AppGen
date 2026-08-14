package org.slcp.service.planning;

import jakarta.validation.Valid;
import java.util.UUID;
import org.slcp.service.planning.PlanningContracts.ActivityRequest;
import org.slcp.service.planning.PlanningContracts.ActivityView;
import org.slcp.service.planning.PlanningContracts.ComponentRequest;
import org.slcp.service.planning.PlanningContracts.ComponentView;
import org.slcp.service.planning.PlanningContracts.PlanView;
import org.slcp.service.planning.PlanningContracts.ResourceAssignmentRequest;
import org.slcp.service.planning.PlanningContracts.ResourceRequest;
import org.slcp.service.planning.PlanningContracts.ResourceView;
import org.slcp.service.planning.PlanningContracts.TaskRequest;
import org.slcp.service.planning.PlanningContracts.TaskView;
import org.slcp.service.planning.PlanningContracts.TimeEntryRequest;
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

/**
 * Descomposicion del trabajo de un proyecto.
 *
 * <p>Las rutas siguen la jerarquia: los componentes cuelgan del entregable, las
 * tareas del componente y las actividades de la tarea, porque a ellos
 * pertenecen. Las operaciones sobre un elemento ya creado se dirigen a el
 * directamente, sin repetir toda la cadena: su identificador lo distingue dentro
 * del proyecto, y arrastrar la ruta entera obligaria a conocer el padre para
 * tocar al hijo.</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/plan")
public class PlanningController {

	private final PlanningService service;

	public PlanningController(PlanningService service) {
		this.service = service;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	/** Plan completo con su avance calculado. */
	@GetMapping
	public PlanView plan(@PathVariable String projectId, @AuthenticationPrincipal Jwt jwt) {
		return service.plan(projectId, quien(jwt));
	}

	// --- Componentes ------------------------------------------------

	@PostMapping("/deliverables/{deliverableId}/components")
	@ResponseStatus(HttpStatus.CREATED)
	public ComponentView crearComponente(@PathVariable String projectId,
			@PathVariable String deliverableId, @Valid @RequestBody ComponentRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.crearComponente(projectId, deliverableId, peticion, quien(jwt));
	}

	@PutMapping("/components/{componentId}")
	public ComponentView editarComponente(@PathVariable String projectId,
			@PathVariable String componentId, @Valid @RequestBody ComponentRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.editarComponente(projectId, componentId, peticion, quien(jwt));
	}

	@DeleteMapping("/components/{componentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarComponente(@PathVariable String projectId,
			@PathVariable String componentId, @AuthenticationPrincipal Jwt jwt) {
		service.eliminarComponente(projectId, componentId, quien(jwt));
	}

	// --- Tareas -----------------------------------------------------

	@PostMapping("/components/{componentId}/tasks")
	@ResponseStatus(HttpStatus.CREATED)
	public TaskView crearTarea(@PathVariable String projectId, @PathVariable String componentId,
			@Valid @RequestBody TaskRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.crearTarea(projectId, componentId, peticion, quien(jwt));
	}

	@PutMapping("/tasks/{taskId}")
	public TaskView editarTarea(@PathVariable String projectId, @PathVariable String taskId,
			@Valid @RequestBody TaskRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.editarTarea(projectId, taskId, peticion, quien(jwt));
	}

	@PutMapping("/tasks/{taskId}/status")
	public TaskView transitarTarea(@PathVariable String projectId, @PathVariable String taskId,
			@RequestParam String to, @AuthenticationPrincipal Jwt jwt) {
		return service.transitarTarea(projectId, taskId, to, quien(jwt));
	}

	@DeleteMapping("/tasks/{taskId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarTarea(@PathVariable String projectId, @PathVariable String taskId,
			@AuthenticationPrincipal Jwt jwt) {
		service.eliminarTarea(projectId, taskId, quien(jwt));
	}

	// --- Actividades ------------------------------------------------

	@PostMapping("/tasks/{taskId}/activities")
	@ResponseStatus(HttpStatus.CREATED)
	public ActivityView crearActividad(@PathVariable String projectId, @PathVariable String taskId,
			@Valid @RequestBody ActivityRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.crearActividad(projectId, taskId, peticion, quien(jwt));
	}

	@PutMapping("/activities/{activityId}")
	public ActivityView editarActividad(@PathVariable String projectId,
			@PathVariable String activityId, @Valid @RequestBody ActivityRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.editarActividad(projectId, activityId, peticion, quien(jwt));
	}

	/** Retira un asiento de horas. Solo quien lo anoto. */
	@DeleteMapping("/activities/{activityId}/time/{entryId}")
	public ActivityView retirarHoras(@PathVariable String projectId,
			@PathVariable String activityId, @PathVariable UUID entryId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.retirarHoras(projectId, activityId, entryId, quien(jwt));
	}

	@PutMapping("/activities/{activityId}/completion")
	public ActivityView marcarActividad(@PathVariable String projectId,
			@PathVariable String activityId, @RequestParam boolean done,
			@AuthenticationPrincipal Jwt jwt) {
		return service.marcarActividad(projectId, activityId, done, quien(jwt));
	}

	@DeleteMapping("/activities/{activityId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarActividad(@PathVariable String projectId,
			@PathVariable String activityId, @AuthenticationPrincipal Jwt jwt) {
		service.eliminarActividad(projectId, activityId, quien(jwt));
	}

	/** Registra horas dedicadas. Las anota quien las dedico, a su nombre. */
	@PostMapping("/activities/{activityId}/time")
	@ResponseStatus(HttpStatus.CREATED)
	public ActivityView registrarHoras(@PathVariable String projectId,
			@PathVariable String activityId, @Valid @RequestBody TimeEntryRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.registrarHoras(projectId, activityId, peticion, quien(jwt));
	}

	// --- Recursos ---------------------------------------------------

	@PostMapping("/resources")
	@ResponseStatus(HttpStatus.CREATED)
	public ResourceView crearRecurso(@PathVariable String projectId,
			@Valid @RequestBody ResourceRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.crearRecurso(projectId, peticion, quien(jwt));
	}

	@PutMapping("/resources/{resourceId}")
	public ResourceView editarRecurso(@PathVariable String projectId,
			@PathVariable String resourceId, @Valid @RequestBody ResourceRequest peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return service.editarRecurso(projectId, resourceId, peticion, quien(jwt));
	}

	@DeleteMapping("/resources/{resourceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarRecurso(@PathVariable String projectId, @PathVariable String resourceId,
			@AuthenticationPrincipal Jwt jwt) {
		service.eliminarRecurso(projectId, resourceId, quien(jwt));
	}

	@PostMapping("/tasks/{taskId}/resources")
	public TaskView asignarRecurso(@PathVariable String projectId, @PathVariable String taskId,
			@RequestBody ResourceAssignmentRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.asignarRecurso(projectId, taskId, peticion, quien(jwt));
	}

	@DeleteMapping("/tasks/{taskId}/resources/{resourceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void retirarRecurso(@PathVariable String projectId, @PathVariable String taskId,
			@PathVariable String resourceId, @AuthenticationPrincipal Jwt jwt) {
		service.retirarRecurso(projectId, taskId, resourceId, quien(jwt));
	}
}
