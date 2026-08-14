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
import org.slcp.service.domain.AiCredential;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.AiPrompt;
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
			/** Que proveedor sirve a esta funcion. */
			String provider,
			String providerLabel,
			/** Si ese proveedor tiene credencial guardada. */
			boolean hasCredential,
			boolean enabled,
			String updatedBy,
			Instant updatedAt) {
	}

	/**
	 * Una credencial guardada. Nunca incluye la clave.
	 *
	 * <p>Varias conviven, una por proveedor: es lo que permite compararlos sin
	 * perder las demas al cambiar de uno.</p>
	 */
	public record CredentialView(
			String provider, String providerLabel, String model, String baseUrl,
			String keyHint, String updatedBy, Instant updatedAt) {
	}

	/**
	 * La instruccion de una funcion, con la de fabrica al lado.
	 *
	 * <p>Se devuelven ambas para que quien edite pueda comparar y volver atras sin
	 * tener que recordar como era.</p>
	 */
	public record PromptView(
			String feature,
			String featureLabel,
			String template,
			String defaultTemplate,
			boolean edited,
			/** Marcas que se sustituyen antes de enviar, con lo que significan. */
			Map<String, String> placeholders,
			String updatedBy,
			Instant updatedAt) {
	}

	public record PromptRequest(String template) {
	}

	/** Un proveedor de los admitidos, con sus valores habituales. */
	public record ProviderView(
			String id, String label, String defaultUrl, String defaultModel, String keysUrl) {
	}

	/** Que proveedor usa una funcion. */
	public record SettingsRequest(String provider) {
	}

	/** Alta o cambio de una credencial. */
	public record CredentialRequest(String model, String baseUrl, String apiKey) {
	}

	/** Aplica una misma configuracion a varias funciones de una vez. */
	public record ApplyToAllRequest(SettingsRequest settings, List<String> features) {
	}

	/** Resultado de la prueba de conexion. */
	public record ProbeResult(boolean ok, String message) {
	}

	private final AiSettingsRepository ajustes;
	private final AiCredentialRepository credenciales;
	private final AiPromptRepository plantillas;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final CredentialCipher cifrador;
	private final Clock clock;

	public AiSettingsService(AiSettingsRepository ajustes, AiCredentialRepository credenciales,
			AiPromptRepository plantillas,
			ProjectService projects,
			UserRepository users, EventRecordRepository events,
			@Value("${slcp.security.master-key:}") String claveMaestra, Clock clock) {

		this.ajustes = ajustes;
		this.credenciales = credenciales;
		this.plantillas = plantillas;
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

		// Una funcion sin configurar apunta al primer proveedor con credencial, no a
		// uno fijo. Apuntar siempre a Anthropic hacia que quien guardara OpenAI viera
		// "no tiene credencial" en las cinco funciones, sin entender por que.
		AiProvider disponible = credenciales
				.findByProjectIdOrderByProviderAsc(proyecto.getId()).stream()
				.findFirst()
				.map(AiCredential::getProvider)
				.orElse(AiProvider.ANTHROPIC);

		List<SettingsView> salida = new ArrayList<>();
		for (AiFeature f : AiFeature.values()) {
			AiSettings s = guardadas.get(f);

			if (s == null) {
				s = AiSettings.inicial(proyecto.getId(), f, solicitante, momento);
				s.configurar(disponible, momento, solicitante);
			}
			salida.add(vistaDe(s));
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
	/** Elige que proveedor sirve a una funcion. */
	@Transactional
	public SettingsView guardar(String projectReadableId, String feature,
			SettingsRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);
		AiFeature funcion = funcionDe(feature);

		AiSettings s = ajustes.findByProjectIdAndFeature(proyecto.getId(), funcion)
				.orElseGet(() -> AiSettings.inicial(proyecto.getId(), funcion, autor, momento));

		s.configurar(proveedorDe(peticion.provider()), momento, autor);
		ajustes.save(s);

		registrar("AI_SETTINGS_SAVED", proyecto.getId(), autor,
				funcion.name() + " -> " + s.getProvider().name(), momento);

		return vistaDe(s);
	}

	/**
	 * Guarda o cambia la credencial de un proveedor.
	 *
	 * <p>Una sola vez por proveedor, aunque la usen cinco funciones. Y varias
	 * conviven: es lo que permite compararlas en un ensayo sin perder las demas.</p>
	 */
	@Transactional
	public CredentialView guardarCredencial(String projectReadableId, String provider,
			CredentialRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);
		AiProvider clase = proveedorDe(provider);

		String cifrada = null;
		String pista = null;

		if (peticion.apiKey() != null && !peticion.apiKey().isBlank()) {
			exigirCifrador();
			String clave = peticion.apiKey().trim();
			cifrada = cifrador.cifrar(clave);
			pista = CredentialCipher.pista(clave);
		}

		AiCredential c = credenciales.findByProjectIdAndProvider(proyecto.getId(), clase)
				.orElse(null);

		if (c == null) {
			if (cifrada == null) {
				throw new GenerationException(
						"Hace falta la clave para guardar una credencial nueva");
			}
			c = AiCredential.crear(proyecto.getId(), clase, peticion.model(), peticion.baseUrl(),
					cifrada, pista, autor, momento);
		} else {
			c.actualizar(peticion.model(), peticion.baseUrl(), cifrada, pista, autor, momento);
		}

		credenciales.save(c);

		// En el registro va la pista, nunca la clave: los registros se leen, se
		// copian y se envian.
		registrar("AI_CREDENTIAL_SAVED", proyecto.getId(), autor,
				clase.name() + " / " + c.getModel() + " credencial " + c.getKeyHint(), momento);

		return vistaDe(c);
	}

	/** Credenciales guardadas del proyecto. Nunca incluyen la clave. */
	@Transactional(readOnly = true)
	public List<CredentialView> credenciales(String projectReadableId, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);

		return credenciales.findByProjectIdOrderByProviderAsc(proyecto.getId()).stream()
				.map(this::vistaDe).toList();
	}

	@Transactional
	public SettingsView activar(String projectReadableId, String feature, boolean activo,
			UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);

		AiSettings s = ajustes.findByProjectIdAndFeature(proyecto.getId(), funcionDe(feature))
				.orElseGet(() -> AiSettings.inicial(proyecto.getId(), funcionDe(feature), autor,
						momento));

		if (activo && credenciales
				.findByProjectIdAndProvider(proyecto.getId(), s.getProvider()).isEmpty()) {

			throw new GenerationException(
					"No hay credencial de " + s.getProvider().getEtiqueta() + " en este proyecto. "
							+ "Guardela antes de activar esta funcion: sin clave quedaria activa de "
							+ "nombre y fallaria en cada uso");
		}

		s.activar(activo, momento, autor);

		ajustes.save(s);
		registrar(activo ? "AI_ENABLED" : "AI_DISABLED", proyecto.getId(), autor,
				s.getProvider().name(), momento);

		return vistaDe(s);
	}

	/**
	 * Retira la credencial de un proveedor.
	 *
	 * <p>Las funciones que lo usaran se desactivan: sin clave quedarian activas de
	 * nombre y fallarian en cada uso.</p>
	 */
	@Transactional
	public void retirarCredencial(String projectReadableId, String provider, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);
		AiProvider clase = proveedorDe(provider);

		credenciales.findByProjectIdAndProvider(proyecto.getId(), clase).ifPresent(c -> {
			ajustes.findByProjectIdOrderByFeatureAsc(proyecto.getId()).stream()
					.filter(s2 -> s2.getProvider() == clase && s2.isEnabled())
					.forEach(s2 -> {
						s2.activar(false, momento, autor);
						ajustes.save(s2);
					});

			credenciales.delete(c);
		});

		registrar("AI_CREDENTIAL_REMOVED", proyecto.getId(), autor, clase.name(), momento);
	}






	/**
	 * Comprueba que un proveedor responde, antes de depender de el.
	 *
	 * <p>Existe porque el fallo de una credencial equivocada aparece al generar, y
	 * ahi se confunde con que el modelo no supo redactar.</p>
	 */
	@Transactional(readOnly = true)
	public ProbeResult probar(String projectReadableId, String provider, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);
		AiProvider clase = proveedorDe(provider);

		Optional<AiCredential> encontrada =
				credenciales.findByProjectIdAndProvider(proyecto.getId(), clase);

		if (encontrada.isEmpty()) {
			return new ProbeResult(false, "No hay credencial guardada de " + clase.getEtiqueta());
		}

		AiCredential c = encontrada.get();
		exigirCifrador();

		try {
			TestGenerator asistido = new AssistedTestGenerator(new DerivedTestGenerator(),
					c.getProvider(), c.getBaseUrl(), cifrador.descifrar(c.getApiKeyCipher()),
					c.getModel(), plantillaDe(proyecto.getId(), AiFeature.GENERATE_TESTS),
					Duration.ofSeconds(20));

			RequirementInput prueba = new RequirementInput("REQ-0000-v1", "PRUEBA", "FUNCTIONAL",
					"Comprobacion de la conexion",
					"El sistema debera responder a una peticion de comprobacion.",
					"Enviar una peticion y comprobar que se recibe respuesta.", null);

			boolean contesto = asistido.generar(prueba, DerivedTestGenerator.ACEPTACION).stream()
					.anyMatch(p -> p.rationale().startsWith("Redactada por el modelo"));

			if (contesto) {
				return new ProbeResult(true,
						clase.getEtiqueta() + " respondio con el modelo " + c.getModel());
			}

			// El generador recurre al derivado cuando falla, de modo que aqui no llega
			// el motivo. Se pide otra vez sin respaldo para poder mostrarlo: sin el,
			// quien comprueba no sabe si falla la clave, el modelo o la cuota.
			return new ProbeResult(false, motivoDe(c));

		} catch (IllegalStateException e) {
			return new ProbeResult(false, e.getMessage());
		}
	}

	/**
	 * Generador de pruebas de la funcion, si esta activa.
	 *
	 * <p>La clave sale de la credencial del proveedor que la funcion tenga
	 * elegido.</p>
	 */
	@Transactional(readOnly = true)
	public Optional<TestGenerator> generadorDe(UUID projectId, TestGenerator respaldo) {
		return activo(projectId, AiFeature.GENERATE_TESTS)
				.map(c -> new AssistedTestGenerator(respaldo, c.getProvider(), c.getBaseUrl(),
						cifrador.descifrar(c.getApiKeyCipher()), c.getModel(),
						plantillaDe(projectId, AiFeature.GENERATE_TESTS), Duration.ofSeconds(15)));
	}

	/** Generador de especificaciones de la funcion, si esta activa. */
	@Transactional(readOnly = true)
	public Optional<SpecificationGenerator> generadorDeEspecificaciones(UUID projectId,
			AiFeature feature, Duration espera) {

		return activo(projectId, feature)
				.map(c -> new AssistedSpecificationGenerator(c.getProvider(), c.getBaseUrl(),
						cifrador.descifrar(c.getApiKeyCipher()), c.getModel(),
						plantillaDe(projectId, feature), espera));
	}

	/**
	 * Generador de pruebas con un proveedor concreto, para el ensayo.
	 *
	 * <p>El ensayo no pregunta cual esta activo: pide uno determinado, y por eso
	 * puede comparar cuatro a la vez. Basta con que tengan credencial.</p>
	 */
	@Transactional(readOnly = true)
	public TestGenerator generadorPara(UUID projectId, AiProvider proveedor,
			TestGenerator respaldo, Duration espera) {

		AiCredential c = exigirCredencial(projectId, proveedor);

		// La misma instruccion para todos los proveedores: es lo que hace valida la
		// comparacion.
		return new AssistedTestGenerator(respaldo, c.getProvider(), c.getBaseUrl(),
				cifrador.descifrar(c.getApiKeyCipher()), c.getModel(),
				plantillaDe(projectId, AiFeature.GENERATE_TESTS), espera);
	}

	/** Generador de especificaciones con un proveedor concreto, para el ensayo. */
	@Transactional(readOnly = true)
	public SpecificationGenerator generadorDeEspecificacionesPara(UUID projectId,
			AiProvider proveedor, Duration espera) {

		AiCredential c = exigirCredencial(projectId, proveedor);

		return new AssistedSpecificationGenerator(c.getProvider(), c.getBaseUrl(),
				cifrador.descifrar(c.getApiKeyCipher()), c.getModel(),
				plantillaDe(projectId, AiFeature.GENERATE_SPECS), espera);
	}

	/**
	 * La instruccion vigente de una funcion.
	 *
	 * <p>La editada si la hay, y si no la de fabrica. Todas las APIs de esa funcion
	 * reciben esta misma.</p>
	 */
	/** Las instrucciones de todas las funciones, editadas o de fabrica. */
	@Transactional(readOnly = true)
	public List<PromptView> prompts(String projectReadableId, UUID solicitante) {
		Project proyecto = exigirFacilitador(projectReadableId, solicitante);

		Map<AiFeature, AiPrompt> guardadas = new EnumMap<>(AiFeature.class);
		plantillas.findByProjectIdOrderByFeatureAsc(proyecto.getId())
				.forEach(p -> guardadas.put(p.getFeature(), p));

		List<PromptView> salida = new ArrayList<>();
		for (AiFeature f : AiFeature.values()) {
			AiPrompt p = guardadas.get(f);
			String fabrica = PromptCatalog.porDefecto(f);

			salida.add(new PromptView(f.name(), f.getEtiqueta(),
					p == null ? fabrica : p.getTemplate(), fabrica, p != null,
					PromptCatalog.marcasDe(f),
					p == null ? null
							: users.findById(p.getUpdatedBy()).map(User::getUsername).orElse(null),
					p == null ? null : p.getUpdatedAt()));
		}
		return salida;
	}

	/**
	 * Guarda una instruccion propia.
	 *
	 * <p>Rige para todas las APIs de esa funcion: si cada una recibiera la suya, un
	 * ensayo compararia las instrucciones y no los modelos.</p>
	 */
	@Transactional
	public PromptView guardarPrompt(String projectReadableId, String feature,
			PromptRequest peticion, UUID autor) {

		Project proyecto = exigirFacilitador(projectReadableId, autor);
		Instant momento = Instant.now(clock);
		AiFeature funcion = funcionDe(feature);

		String texto = peticion.template() == null ? "" : peticion.template().trim();
		if (texto.length() < 40) {
			throw new GenerationException(
					"La instruccion es demasiado corta. Una instruccion vacia o casi vacia deja al "
							+ "modelo sin nada que hacer y devolvera cualquier cosa");
		}

		AiPrompt p = plantillas.findByProjectIdAndFeature(proyecto.getId(), funcion)
				.orElseGet(() -> AiPrompt.crear(proyecto.getId(), funcion, texto, autor, momento));

		p.actualizar(texto, autor, momento);
		plantillas.save(p);

		registrar("AI_PROMPT_SAVED", proyecto.getId(), autor, funcion.name(), momento);

		return prompts(projectReadableId, autor).stream()
				.filter(v -> v.feature().equals(funcion.name())).findFirst().orElseThrow();
	}

	/**
	 * Vuelve a la instruccion de fabrica.
	 *
	 * <p>Se borra la propia en lugar de copiar la de fabrica: asi la de fabrica
	 * sigue mejorando con las versiones sin que nadie arrastre una copia vieja.</p>
	 */
	@Transactional
	public PromptView restaurarPrompt(String projectReadableId, String feature, UUID autor) {
		Project proyecto = exigirFacilitador(projectReadableId, autor);
		AiFeature funcion = funcionDe(feature);

		plantillas.findByProjectIdAndFeature(proyecto.getId(), funcion)
				.ifPresent(plantillas::delete);

		registrar("AI_PROMPT_RESTORED", proyecto.getId(), autor, funcion.name(),
				Instant.now(clock));

		return prompts(projectReadableId, autor).stream()
				.filter(v -> v.feature().equals(funcion.name())).findFirst().orElseThrow();
	}

	@Transactional(readOnly = true)
	public String plantillaDe(UUID projectId, AiFeature feature) {
		return plantillas.findByProjectIdAndFeature(projectId, feature)
				.map(org.slcp.service.domain.AiPrompt::getTemplate)
				.orElseGet(() -> PromptCatalog.porDefecto(feature));
	}

	/**
	 * Pregunta al proveedor y devuelve lo que respondio.
	 *
	 * <p>Se llama sin respaldo derivado a proposito: aqui interesa el fallo, no un
	 * resultado alternativo.</p>
	 */
	private String motivoDe(AiCredential c) {
		try {
			new AssistedSpecificationGenerator(c.getProvider(), c.getBaseUrl(),
					cifrador.descifrar(c.getApiKeyCipher()), c.getModel(),
					PromptCatalog.porDefecto(AiFeature.GENERATE_SPECS), Duration.ofSeconds(20))
					.generar(List.of(new RequirementInput("REQ-0000-v1", "PRUEBA", "FUNCTIONAL",
							"Comprobacion", "El sistema debera responder.", "Comprobar.", null)),
							"USER_STORY");

			return "El servicio respondio, pero no en la forma esperada. Modelo: " + c.getModel();

		} catch (RuntimeException e) {
			String detalle = e.getMessage() == null ? "sin detalle" : e.getMessage();
			return "Modelo " + c.getModel() + ". El proveedor respondio: " + detalle;
		}
	}

	/** La credencial del proveedor que sirve a una funcion activa. */
	private Optional<AiCredential> activo(UUID projectId, AiFeature feature) {
		if (cifrador == null) {
			return Optional.empty();
		}
		return ajustes.findByProjectIdAndFeature(projectId, feature)
				.filter(AiSettings::isEnabled)
				.flatMap(s -> credenciales.findByProjectIdAndProvider(projectId, s.getProvider()));
	}

	private AiCredential exigirCredencial(UUID projectId, AiProvider proveedor) {
		exigirCifrador();

		return credenciales.findByProjectIdAndProvider(projectId, proveedor)
				.orElseThrow(() -> new GenerationException(
						proveedor.getEtiqueta() + " no tiene credencial guardada en este proyecto"));
	}

	// =================================================================

	private SettingsView vistaDe(AiSettings s) {
		String quien = users.findById(s.getUpdatedBy()).map(User::getUsername).orElse(null);

		boolean tieneCredencial = credenciales
				.findByProjectIdAndProvider(s.getProjectId(), s.getProvider()).isPresent();

		return new SettingsView(s.getFeature().name(), s.getFeature().getEtiqueta(),
				s.getFeature().getQueHace(), s.getFeature().esImprescindible(),
				s.getProvider().name(), s.getProvider().getEtiqueta(), tieneCredencial,
				s.isEnabled(), quien, s.getUpdatedAt());
	}

	private CredentialView vistaDe(AiCredential c) {
		return new CredentialView(c.getProvider().name(), c.getProvider().getEtiqueta(),
				c.getModel(), c.getBaseUrl(), c.getKeyHint(),
				users.findById(c.getUpdatedBy()).map(User::getUsername).orElse(null),
				c.getUpdatedAt());
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
