package org.slcp.service.generation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.AiProvider;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementStatus;
import org.slcp.service.domain.Specification;
import org.slcp.service.domain.User;
import org.slcp.service.ingestion.JsonParser;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.slcp.service.requirements.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensayo comparativo de proveedores sobre los requisitos del proyecto.
 *
 * <p>Que proveedor sirve mejor a cada funcion no puede saberse de antemano: las
 * comparativas publicadas miden con conjuntos de proposito general, no con
 * requisitos de un dominio concreto en castellano. Lo que si puede hacerse es
 * medirlo aqui, con lo que la plataforma ya sabe calcular.</p>
 *
 * <p>Las medidas no son opiniones sobre calidad: son cuentas. Cuantas propuestas
 * quedan sin huecos, cuantas cifras invento cada modelo --- que la salvaguarda
 * sustituye y por tanto puede contarse ---, cuantos reparos trae el validador, y
 * cuanto tardo. La medida que de verdad importa, cuantas acepta el equipo sin
 * tocarlas, la aporta el uso posterior y no este ensayo.</p>
 *
 * <p>Nada de lo ensayado se guarda como artefacto del proyecto. Un ensayo que
 * dejara veinte pruebas sueltas por ahi obligaria a limpiarlas despues, y nadie
 * sabria cuales eran del ensayo y cuales del trabajo.</p>
 */
@Service
public class BenchmarkService {

	/** Peticion de ensayo. */
	public record BenchmarkRequest(
			String feature,
			String subkind,
			/** Requisitos concretos, o vacio para tomar unos pocos aprobados. */
			List<String> requirements,
			/** Proveedores a comparar. Han de estar configurados en el proyecto. */
			List<String> providers,
			String notes) {
	}

	/** Lo que un proveedor produjo, con sus medidas. */
	public record ResultView(
			String provider,
			String providerLabel,
			String model,
			int produced,
			/** Sin huecos: listas para ejecutar tal cual. */
			int complete,
			/** Cifras que invento y la salvaguarda sustituyo. Menos es mejor. */
			int invented,
			int issues,
			long elapsedMs,
			boolean failed,
			String failureReason,
			String sample) {
	}

	public record RunView(
			String id,
			String feature,
			String featureLabel,
			String subkind,
			List<String> requirements,
			String runBy,
			Instant runAt,
			String notes,
			List<ResultView> results) {
	}

	private final BenchmarkRepository benchmarks;
	private final AiCredentialRepository credenciales;
	private final AiSettingsService ia;
	private final RequirementRepository requirements;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final Clock clock;

	public BenchmarkService(BenchmarkRepository benchmarks, AiCredentialRepository credenciales,
			AiSettingsService ia, RequirementRepository requirements, ProjectService projects,
			UserRepository users, EventRecordRepository events, Clock clock) {

		this.benchmarks = benchmarks;
		this.credenciales = credenciales;
		this.ia = ia;
		this.requirements = requirements;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	// =================================================================

	@Transactional(readOnly = true)
	public List<RunView> historial(String projectReadableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);

		return benchmarks.findByProjectIdOrderByRunAtDesc(proyecto.getId()).stream()
				.map(this::vistaDe).toList();
	}

	/**
	 * Ejecuta el ensayo.
	 *
	 * <p>Se llama a cada proveedor con los mismos requisitos y la misma
	 * instruccion: comparar con entradas distintas no compararia los proveedores,
	 * sino las entradas.</p>
	 */
	@Transactional
	public RunView ejecutar(String projectReadableId, BenchmarkRequest peticion, UUID autor) {
		Project proyecto = exigirEquipo(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		AiFeature funcion = funcionDe(peticion.feature());
		List<Requirement> elegidos = requisitosDe(proyecto.getId(), peticion.requirements());

		if (elegidos.isEmpty()) {
			throw new GenerationException(
					"No hay requisitos aprobados con los que ensayar. El ensayo mide sobre los "
							+ "requisitos del proyecto, que es lo unico que dice algo de su caso");
		}

		if (peticion.providers().size() < 2) {
			throw new GenerationException(
					"Un ensayo compara: elija al menos dos proveedores. Con uno solo se obtiene "
							+ "una medida sin nada con que contrastarla");
		}

		// Se guarda la instruccion con la que se ensayo: sin ese dato, un ensayo de
		// hace un mes no puede compararse con el de hoy si alguien la edito entre
		// medias.
		String instruccion = ia.plantillaDe(proyecto.getId(), funcion);

		UUID runId = UUID.randomUUID();
		benchmarks.crearEnsayo(runId, proyecto.getId(), funcion.name(),
				elegidos.stream().map(Requirement::getReadableId).reduce((a, b) -> a + ", " + b)
						.orElse(""),
				peticion.subkind(), autor, momento, peticion.notes(), instruccion);

		List<RequirementInput> entrada = elegidos.stream().map(this::entradaDe).toList();

		for (String proveedor : peticion.providers()) {
			medir(runId, proyecto.getId(), funcion, proveedor, entrada, peticion.subkind());
		}

		registrar("BENCHMARK_RUN", proyecto.getId(), autor,
				funcion.name() + " con " + peticion.providers().size() + " proveedores sobre "
						+ elegidos.size() + " requisitos", momento);

		return vistaDe(benchmarks.findById(runId).orElseThrow());
	}

	// =================================================================

	/**
	 * Llama a un proveedor y cuenta lo que produjo.
	 *
	 * <p>Un fallo no detiene el ensayo: se anota como fallo con su motivo y se
	 * sigue con el siguiente. Que un proveedor no responda es un resultado, y
	 * cancelar el ensayo entero por ello perderia lo que los demas si dieron.</p>
	 */
	private void medir(UUID runId, UUID projectId, AiFeature funcion, String proveedor,
			List<RequirementInput> entrada, String subkind) {

		AiProvider clase;
		try {
			clase = AiProvider.valueOf(proveedor);
		} catch (IllegalArgumentException e) {
			benchmarks.anotarFallo(UUID.randomUUID(), runId, proveedor, "-",
					"Proveedor no reconocido");
			return;
		}

		// Basta con que tenga credencial: el ensayo no pregunta cual esta activo,
		// que es justo lo que permite comparar cuatro a la vez.
		var credencial = credenciales.findByProjectIdAndProvider(projectId, clase);

		if (credencial.isEmpty()) {
			// Se dice en lugar de saltarlo: quien pidio comparar cuatro proveedores
			// ha de ver por que solo hay tres columnas.
			benchmarks.anotarFallo(UUID.randomUUID(), runId, proveedor, "-",
					"No tiene credencial guardada en este proyecto. Guardela en Servicios de IA y "
							+ "vuelva a ensayar");
			return;
		}

		String modelo = credencial.get().getModel();
		long desde = System.currentTimeMillis();

		try {
			Medida m = switch (funcion) {
				case GENERATE_TESTS, VALIDATE_REQUIREMENTS, GENERATE_DIAGRAMS ->
						medirPruebas(projectId, clase, entrada, subkind);
				case GENERATE_SPECS, GENERATE_CODE ->
						medirEspecificaciones(projectId, clase, entrada, subkind);
			};

			benchmarks.anotarResultado(UUID.randomUUID(), runId, proveedor, modelo,
					m.producidas(), m.completas(), m.inventadas(), m.reparos(),
					(int) (System.currentTimeMillis() - desde), m.muestra());

		} catch (RuntimeException e) {
			benchmarks.anotarFallo(UUID.randomUUID(), runId, proveedor, modelo,
					e.getMessage() == null ? "Error sin mensaje" : e.getMessage());
		}
	}

	/** Lo que se cuenta de una tanda. */
	private record Medida(int producidas, int completas, int inventadas, int reparos,
			String muestra) {
	}

	private Medida medirPruebas(UUID projectId, AiProvider proveedor,
			List<RequirementInput> entrada, String subkind) {

		TestGenerator generador = ia.generadorPara(projectId, proveedor, new DerivedTestGenerator(),
				Duration.ofSeconds(60));

		String clase = subkind == null || subkind.isBlank()
				? DerivedTestGenerator.ACEPTACION : subkind;

		int producidas = 0;
		int completas = 0;
		int inventadas = 0;
		String muestra = null;

		for (RequirementInput r : entrada) {
			for (ArtifactProposal p : generador.generar(r, clase)) {
				producidas++;

				if (!p.needsDecision()) {
					completas++;
				}
				inventadas += cifrasSustituidas(p.rationale());

				if (muestra == null) {
					muestra = p.content();
				}
			}
		}
		return new Medida(producidas, completas, inventadas, 0, muestra);
	}

	private Medida medirEspecificaciones(UUID projectId, AiProvider proveedor,
			List<RequirementInput> entrada, String subkind) {

		SpecificationGenerator generador = ia.generadorDeEspecificacionesPara(projectId, proveedor,
				Duration.ofSeconds(90));

		String clase = subkind == null || subkind.isBlank()
				? Specification.CASO_DE_USO : subkind;

		int producidas = 0;
		int completas = 0;
		int reparos = 0;
		String muestra = null;

		for (RequirementInput r : entrada) {
			for (SpecificationGenerator.Resultado resultado
					: generador.generar(List.of(r), clase)) {

				producidas++;
				if (resultado.huecos().isEmpty()) {
					completas++;
				}

				// El validador es el mismo que se aplica al guardar: se mide con la
				// misma vara con la que despues se juzgara lo que se produzca.
				Object campos = JsonParser.analizar(resultado.contenido());
				if (campos instanceof Map<?, ?> mapa) {
					reparos += SpecificationValidator.revisar(mapa, clase).size();
				}

				if (muestra == null) {
					muestra = resultado.contenido();
				}
			}
		}
		return new Medida(producidas, completas, 0, reparos, muestra);
	}

	/**
	 * Cuenta las cifras que la salvaguarda sustituyo.
	 *
	 * <p>Sale del propio fundamento, que ya declara cuantas hubo. Es la medida mas
	 * directa de cuanto se inventa cada modelo, y no hay otra forma de obtenerla
	 * sin volver a comparar los textos.</p>
	 */
	private int cifrasSustituidas(String fundamento) {
		if (fundamento == null) {
			return 0;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("Se sustituyeron (\\d+) cifras").matcher(fundamento);

		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** Unos pocos requisitos aprobados, si no se eligieron. */
	private List<Requirement> requisitosDe(UUID projectId, List<String> readableIds) {
		List<Requirement> aprobados = requirements
				.findByProjectIdAndStatusOrderByReadableIdAsc(projectId, RequirementStatus.APPROVED);

		if (readableIds == null || readableIds.isEmpty()) {
			// Tres bastan para comparar y no agotan la cuota de nadie. Ensayar con
			// veinte multiplicaria el gasto sin cambiar la conclusion.
			return aprobados.stream().limit(3).toList();
		}

		Set<String> pedidos = new HashSet<>(readableIds);
		return aprobados.stream().filter(r -> pedidos.contains(r.getReadableId())).toList();
	}

	private RequirementInput entradaDe(Requirement r) {
		return new RequirementInput(r.getReadableId(), r.getSourceId(), r.getKind().name(),
				r.getName(), r.getStatement(), r.getVerification(), r.getActor());
	}

	private RunView vistaDe(org.slcp.service.domain.BenchmarkRun run) {
		List<ResultView> resultados = new ArrayList<>();

		for (Object[] f : benchmarks.resultadosDe(run.getId())) {
			String proveedor = (String) f[0];
			String etiqueta = proveedor;
			try {
				etiqueta = AiProvider.valueOf(proveedor).getEtiqueta();
			} catch (IllegalArgumentException e) {
				// Se deja el identificador: es mejor que no mostrar nada.
			}

			resultados.add(new ResultView(proveedor, etiqueta, (String) f[1],
					((Number) f[2]).intValue(), ((Number) f[3]).intValue(),
					((Number) f[4]).intValue(), ((Number) f[5]).intValue(),
					((Number) f[6]).longValue(), (Boolean) f[7], (String) f[8], (String) f[9]));
		}

		AiFeature funcion = AiFeature.valueOf(run.getFeature());

		return new RunView(run.getId().toString(), run.getFeature(), funcion.getEtiqueta(),
				run.getSubkind(), List.of(run.getRequirements().split(", ")),
				users.findById(run.getRunBy()).map(User::getUsername).orElse(null),
				run.getRunAt(), run.getNotes(), resultados);
	}

	private AiFeature funcionDe(String valor) {
		try {
			return AiFeature.valueOf(valor);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new GenerationException("Funcion no reconocida: " + valor);
		}
	}

	private Project exigirEquipo(String readableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(readableId, solicitante);

		if (!projects.rolesEn(proyecto.getId(), solicitante).contains(ProjectRole.TEAM_MEMBER)) {
			throw new ProjectAccessException(
					"El ensayo lo ejecuta el equipo de desarrollo: consume cuota de los servicios "
							+ "configurados");
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle,
			Instant momento) {

		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
