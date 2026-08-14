package org.slcp.service.requirements;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slcp.service.requirements.RequirementContracts.ImportRequest;
import org.slcp.service.requirements.RequirementContracts.AcceptHeldRequest;
import org.slcp.service.requirements.RequirementReportService.RequirementReport;
import org.slcp.service.requirements.RequirementContracts.CheckRequest;
import org.slcp.service.requirements.RequirementContracts.CheckResult;
import org.slcp.service.requirements.RequirementContracts.ImportResult;
import org.slcp.service.requirements.RequirementContracts.RequirementRequest;
import org.slcp.service.requirements.RequirementContracts.RequirementSummary;
import org.slcp.service.requirements.RequirementContracts.RequirementView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Requisitos de un proyecto.
 *
 * <p>Cuelgan del proyecto porque a el pertenecen, y su alcance se autoriza por
 * membresia como todo lo demas.</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/requirements")
public class RequirementController {

	private final RequirementService service;
	private final RequirementReportService report;

	public RequirementController(RequirementService service, RequirementReportService report) {
		this.service = service;
		this.report = report;
	}

	private UUID quien(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	@GetMapping
	public List<RequirementView> listar(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.listar(projectId, quien(jwt));
	}

	@GetMapping("/summary")
	public RequirementSummary resumen(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return service.resumen(projectId, quien(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RequirementView crear(@PathVariable String projectId,
			@Valid @RequestBody RequirementRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.crear(projectId, peticion, quien(jwt));
	}

	/**
	 * Carga un documento completo.
	 *
	 * <p>El contenido viaja como texto y no como archivo adjunto: la interfaz lo
	 * lee en el navegador y lo envia, lo que evita la codificacion en varias
	 * partes sin perder nada.</p>
	 */
	/**
	 * Comprueba un enunciado antes de darlo de alta.
	 *
	 * <p>Es POST y no GET porque el enunciado va en el cuerpo: llevarlo en la
	 * direccion lo dejaria escrito en los registros del servidor y limitado en
	 * longitud.</p>
	 */
	@PostMapping("/check")
	public CheckResult comprobar(@PathVariable String projectId,
			@RequestBody CheckRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.comprobar(projectId, peticion.statement(), quien(jwt));
	}

	/**
	 * Da de alta requisitos que quedaron retenidos por no parecer del proyecto.
	 *
	 * <p>Corresponde a quien produce: tanto al miembro del equipo como al
	 * facilitador, que lo es tambien de sus proyectos.</p>
	 */
	/**
	 * Informe de los requisitos con todo lo que consta de ellos.
	 *
	 * <p>Lo puede pedir cualquiera con acceso al proyecto: es lectura de lo que ya
	 * esta, repartido por las pantallas.</p>
	 */
	@GetMapping("/report")
	public RequirementReport informe(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return report.componer(projectId, quien(jwt));
	}

	/**
	 * Exporta el informe. Corresponde al equipo y al facilitador.
	 *
	 * <p>Se genera en el servicio y no en el navegador porque hay una restriccion
	 * que cumplir: un archivo armado con datos ya enviados no restringe nada.</p>
	 */
	@GetMapping(value = "/report/export", produces = "text/csv")
	public ResponseEntity<byte[]> exportar(@PathVariable String projectId,
			@AuthenticationPrincipal Jwt jwt) {

		String csv = report.exportar(projectId, quien(jwt));

		// La marca de orden de bytes hace que Excel lo abra como UTF-8; sin ella,
		// las tildes salen partidas y el informe parece corrupto.
		byte[] cuerpo = ("\ufeff" + csv).getBytes(StandardCharsets.UTF_8);

		return ResponseEntity.ok()
				.header("Content-Disposition",
						"attachment; filename=\"requisitos-" + projectId + ".csv\"")
				.body(cuerpo);
	}

	@PostMapping("/held")
	public ImportResult aceptarRetenidos(@PathVariable String projectId,
			@RequestBody AcceptHeldRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.aceptarRetenidos(projectId, peticion, quien(jwt));
	}

	@PostMapping("/import")
	public ImportResult importar(@PathVariable String projectId,
			@Valid @RequestBody ImportRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.importar(projectId, peticion, quien(jwt));
	}

	/**
	 * Modifica un requisito.
	 *
	 * @param fromSuggestion si el texto procede de una propuesta aceptada, para
	 *                       registrar su procedencia conforme a ANA-16
	 */
	@PutMapping("/{readableId}")
	public RequirementView editar(@PathVariable String projectId, @PathVariable String readableId,
			@RequestParam(defaultValue = "false") boolean fromSuggestion,
			@Valid @RequestBody RequirementRequest peticion, @AuthenticationPrincipal Jwt jwt) {
		return service.editar(projectId, readableId, peticion, fromSuggestion, quien(jwt));
	}

	@DeleteMapping("/{readableId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable String projectId, @PathVariable String readableId,
			@AuthenticationPrincipal Jwt jwt) {
		service.eliminar(projectId, readableId, quien(jwt));
	}

	@PutMapping("/{readableId}/status")
	public RequirementView transitar(@PathVariable String projectId,
			@PathVariable String readableId, @RequestParam String to,
			@AuthenticationPrincipal Jwt jwt) {
		return service.transitar(projectId, readableId, to, quien(jwt));
	}
}
