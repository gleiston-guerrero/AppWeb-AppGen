package org.slcp.service.generation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.AiProvider;
import org.slcp.service.domain.AiSettings;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.User;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuracion del servicio de IA generativa de cada proyecto.
 *
 * <p>La credencial entra y no vuelve a salir. Lo que se devuelve es una pista de
 * cuatro caracteres: basta para que quien la puso reconozca cual esta y no
 * alcanza para usarla. Una interfaz que muestre la clave para "comprobar que es
 * la correcta" la expone a cualquiera que mire la pantalla, y la deja en el
 * historial del navegador.</p>
 *
 * <p>Configurarla corresponde al facilitador del proyecto: es quien responde del
 * gasto que ese servicio genere.</p>
 */
@Service
public class AiSettingsService {

	/** Lo que la interfaz puede saber. Nunca incluye la credencial. */
	public record SettingsView(
			String feature,
			String featureLabel,
			String featureDescription,
			/** Si la funcion no puede realizarse sin modelo. */
			boolean essential,
			String provider,
			String providerLabel,
			String model,
			String baseUrl,
			/** Cuatro ultimos caracteres de la clave, o nulo si no hay. */
			String keyHint,
			boolean hasKey,
			boolean enabled,
			String updatedBy,
			Instant updatedAt) {
	}

	/** Un proveedor de los admitidos, con sus valores habituales. */
	public record ProviderView(
			String id, String label, String defaultUrl, String defaultModel, String keysUrl) {
	}

	public record SettingsRequest(String provider, String model, String baseUrl, String apiKey) {
	}

	/** Aplica una misma configuracion a varias funciones de una vez. */
	public record ApplyToAllRequest(SettingsRequest settings, List<String> features) {
	}

	/** Resultado de la prueba de conexion. */
	public record ProbeResult(boolean ok, String message) {
	}

	private final AiSettingsRepository ajustes;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final CredentialCipher cifrador;
	private final Clock clock;

	public AiSettingsService(AiSettingsRepository ajustes, ProjectService projects,
			UserRepository users, EventRecordRepository events,
			@Value("${slcp.security.master-key:}") String claveMaestra, Clock clock) {

		this.ajustes = ajustes;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.clock = clock;

		// Se construye aunque no haya clave maestra: la plataforma ha de arrancar
		// sin ella, y solo falla quien intente guardar una credencial.
		CredentialCipher construido = null;
		try {
			construido = new CredentialCipher(claveMaestra);
		} catch (IllegalStateException e) {
			construido = null;
		}
		this.cifrador = construido;
	}

	// =================================================================

	/** Proveedores admitidos, con sus valores habituales. */
	public List<ProviderView> proveedores() {
		List<ProviderView> salida = new ArrayList<>();
		for (AiProvider p : AiProvider.values()) {
			salida.add(new ProviderView(p.name(), p.getEtiqueta(), p.getDireccionPorDefecto(),
					p.getModeloPorDefecto(), p.getDondeConseguirLaClave()));
		}
		return salida;
	}

	/**
	 * Configuracion de todas las funciones.
	 *
	 * <p>Se devuelven todas, incluidas las que nadie ha configurado: la pantalla ha
	 * de mostrar que puede configurarse, no solo lo que ya lo esta.</p>
	 */
	@Transactional(readOnly = true)
	public List<SettingsView> consultar(String projectReadableId, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);
		Instant momento = Instant.now(clock);

		Map<AiFeature, AiSettings> guardadas = new EnumMap<>(AiFeature.class);
		ajustes.findByProjectIdOrderByFeatureAsc(proyecto.getId())
				.forEach(s -> guardadas.put(s.getFeature(), s));

		List<SettingsView> salida = new ArrayList<>();
		for (AiFeature f : AiFeature.values()) {
			AiSettings s = guardadas.get(f);
			salida.add(vistaDe(s != null ? s
					: AiSettings.inicial(proyecto.getId(), f, solicitante, momento)));
		}
		return salida;
	}

	/**
	 * Guarda la configuracion.
	 *
	 * <p>La credencial solo se toca si viene una nueva: enviar el formulario sin
	 * ella conserva la que hubiera. De otro modo, corregir el nombre del modelo
	 * obligaria a volver a teclear la clave, y quien no la tuviera a mano la
	 * borraria sin querer.</p>
	 */
	@Transactional
	public SettingsView guardar(String projectReadableId, String feature,
			SettingsRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);
		AiFeature funcion = funcionDe(feature);

		AiSettings s = ajustes.findByProjectIdAndFeature(proyecto.getId(), funcion)
				.orElseGet(() -> AiSettings.inicial(proyecto.getId(), funcion, autor, momento));

		AiProvider proveedor = peticion.provider() == null || peticion.provider().isBlank()
				? null
				: proveedorDe(peticion.provider());

		s.configurar(proveedor, peticion.model(), peticion.baseUrl(), momento, autor);

		if (peticion.apiKey() != null && !peticion.apiKey().isBlank()) {
			exigirCifrador();
			String clave = peticion.apiKey().trim();
			s.guardarCredencial(cifrador.cifrar(clave), CredentialCipher.pista(clave), momento, autor);
		}

		ajustes.save(s);

		// En el registro va la pista, nunca la clave: los registros se leen, se
		// copian y se envian, y una credencial en ellos deja de ser un secreto.
		registrar("AI_SETTINGS_SAVED", proyecto.getId(), autor,
				funcion.name() + ": " + s.getProvider().name() + " / " + s.getModel()
						+ (s.getKeyHint() == null ? " sin credencial" : " con credencial " + s.getKeyHint()),
				momento);

		return vistaDe(s);
	}

	@Transactional
	public SettingsView activar(String projectReadableId, String feature, boolean activo,
			UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		AiSettings s = ajustes.findByProjectIdAndFeature(proyecto.getId(), funcionDe(feature))
				.orElseThrow(() -> new GenerationException(
						"Esa funcion no tiene configuracion. Guardela antes de activarla"));

		try {
			s.activar(activo, momento, autor);
		} catch (IllegalStateException e) {
			throw new GenerationException(e.getMessage());
		}

		ajustes.save(s);
		registrar(activo ? "AI_ENABLED" : "AI_DISABLED", proyecto.getId(), autor,
				s.getProvider().name(), momento);

		return vistaDe(s);
	}

	@Transactional
	public SettingsView retirarCredencial(String projectReadableId, String feature, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		AiSettings s = ajustes.findByProjectIdAndFeature(proyecto.getId(), funcionDe(feature))
				.orElseThrow(() -> new GenerationException("Esa funcion no tiene configuracion"));

		s.retirarCredencial(momento, autor);
		ajustes.save(s);

		registrar("AI_CREDENTIAL_REMOVED", proyecto.getId(), autor, s.getProvider().name(), momento);
		return vistaDe(s);
	}

	/**
	 * Comprueba que la configuracion sirve, con una llamada minima.
	 *
	 * <p>Existe porque el fallo de una credencial equivocada aparece al generar, y
	 * ahi se confunde con que el modelo no supo redactar la prueba. Comprobarlo
	 * antes separa las dos cosas.</p>
	 */
	@Transactional(readOnly = true)
	public ProbeResult probar(String projectReadableId, String feature, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);

		Optional<AiSettings> encontrada =
				ajustes.findByProjectIdAndFeature(proyecto.getId(), funcionDe(feature));

		if (encontrada.isEmpty() || !encontrada.get().tieneCredencial()) {
			return new ProbeResult(false, "Esa funcion no tiene credencial guardada");
		}

		AiSettings s = encontrada.get();
		exigirCifrador();

		try {
			TestGenerator asistido = new AssistedTestGenerator(new DerivedTestGenerator(),
					s.getProvider(), s.getBaseUrl(), cifrador.descifrar(s.getApiKeyCipher()),
					s.getModel(), Duration.ofSeconds(15));

			RequirementInput prueba = new RequirementInput("REQ-0000-v1", "PRUEBA", "FUNCTIONAL",
					"Comprobacion de la conexion",
					"El sistema debera responder a una peticion de comprobacion.",
					"Enviar una peticion y comprobar que se recibe respuesta.", "Sistema");

			List<ArtifactProposal> resultado = asistido.generar(prueba,
					DerivedTestGenerator.ACEPTACION);

			boolean contesto = resultado.stream()
					.anyMatch(p -> p.rationale().startsWith("Redactada por el modelo"));

			return contesto
					? new ProbeResult(true, "El servicio respondio correctamente con "
							+ s.getProvider().getEtiqueta() + " y el modelo " + s.getModel())
					: new ProbeResult(false, "El servicio no respondio y se empleo la generacion "
							+ "derivada. Compruebe la credencial, la direccion y el nombre del modelo");

		} catch (IllegalStateException e) {
			return new ProbeResult(false, e.getMessage());
		}
	}

	/**
	 * Devuelve el generador configurado del proyecto, si lo hay y esta activo.
	 *
	 * <p>Se resuelve por proyecto y en cada uso, no una vez al arrancar: la
	 * configuracion cambia sin reiniciar, y un generador construido al arranque
	 * seguiria usando la credencial anterior.</p>
	 */
	@Transactional(readOnly = true)
	public Optional<TestGenerator> generadorDe(UUID projectId, TestGenerator respaldo) {
		return ajustes.findByProjectIdAndFeature(projectId, AiFeature.GENERATE_TESTS)
				.filter(AiSettings::isEnabled)
				.filter(AiSettings::tieneCredencial)
				.filter(s -> cifrador != null)
				.map(s -> new AssistedTestGenerator(respaldo, s.getProvider(), s.getBaseUrl(),
						cifrador.descifrar(s.getApiKeyCipher()), s.getModel(),
						Duration.ofSeconds(15)));
	}

	// =================================================================

	private SettingsView vistaDe(AiSettings s) {
		String quien = users.findById(s.getUpdatedBy()).map(User::getUsername).orElse(null);

		return new SettingsView(s.getFeature().name(), s.getFeature().getEtiqueta(),
				s.getFeature().getQueHace(), s.getFeature().esImprescindible(),
				s.getProvider().name(), s.getProvider().getEtiqueta(),
				s.getModel(), s.getBaseUrl(), s.getKeyHint(), s.tieneCredencial(), s.isEnabled(),
				quien, s.getUpdatedAt());
	}

	private AiFeature funcionDe(String valor) {
		try {
			return AiFeature.valueOf(valor);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new GenerationException("Funcion no reconocida: " + valor);
		}
	}

	private AiProvider proveedorDe(String valor) {
		try {
			return AiProvider.valueOf(valor);
		} catch (IllegalArgumentException e) {
			throw new GenerationException("Proveedor no reconocido: " + valor);
		}
	}

	private void exigirCifrador() {
		if (cifrador == null) {
			throw new GenerationException(
					"Esta instalacion no tiene clave maestra de cifrado configurada "
							+ "(slcp.security.master-key). Sin ella no pueden guardarse credenciales: "
							+ "guardarlas en claro seria peor que no admitirlas");
		}
	}

	private Project exigirFacilitador(String readableId, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(readableId, solicitante);

		if (!projects.rolesEn(proyecto.getId(), solicitante)
				.contains(ProjectRole.PROJECT_FACILITATOR)) {

			throw new ProjectAccessException(
					"Configurar el servicio de IA corresponde al facilitador del proyecto: es quien "
							+ "responde del gasto que genere");
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle,
			Instant momento) {

		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
