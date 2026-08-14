package org.slcp.service.generation;

import java.util.List;
import java.util.UUID;
import org.slcp.service.generation.BenchmarkService.BenchmarkRequest;
import org.slcp.service.generation.BenchmarkService.RunView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ensayo comparativo de proveedores sobre los requisitos del proyecto. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/benchmarks")
public class BenchmarkController {

	private final BenchmarkService service;

	public BenchmarkController(BenchmarkService service) {
		this.service = service;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	/** Ensayos anteriores, del mas reciente al mas antiguo. */
	@GetMapping
	public List<RunView> historial(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.historial(projectId, quien(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RunView ejecutar(@PathVariable String projectId,
			@RequestBody BenchmarkRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.ejecutar(projectId, peticion, quien(jwt));
	}
}
