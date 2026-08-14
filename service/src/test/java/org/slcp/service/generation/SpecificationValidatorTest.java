package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.generation.SpecificationValidator.Reparo;

/**
 * Oraculo de la comprobacion de casos de uso e historias.
 *
 * <p>Los campos son los de las tablas 8 y 9 del manuscrito y la forma es la del
 * caso de uso expandido de Larman.</p>
 */
class SpecificationValidatorTest {

	private static Map<String, Object> paso(int n, String actor, String sistema) {
		return Map.of("numero", n, "accionDelActor", actor == null ? "" : actor,
				"respuestaDelSistema", sistema == null ? "" : sistema, "referencia", "");
	}

	private static Map<String, Object> desvio(String n, String condicion, String respuesta,
			int desde) {
		return Map.of("numero", n, "condicion", condicion, "respuesta", respuesta,
				"desdeElPaso", desde);
	}

	/** El caso de uso de registro, tal como quedo acordado. */
	private Map<String, Object> casoBueno() {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("nombre", "Registrar usuario");
		c.put("actorPrincipal", "Persona no registrada");
		c.put("actoresSecundarios", List.of());
		c.put("objetivo", "Que una persona quede registrada como usuario del sistema.");
		c.put("precondiciones", List.of("Ninguna."));
		c.put("flujoPrincipal", List.of(
				paso(1, "Este caso de uso inicia cuando la persona solicita registrarse.",
						"El sistema presenta los datos que ha de aportar."),
				paso(2, "La persona aporta los datos y confirma el registro.",
						"El sistema comprueba que esten completos y bien formados. (ver E-1)"),
				paso(3, null, "El sistema comprueba que el correo no este en uso. (ver E-2)"),
				paso(4, null, "El sistema registra al usuario."),
				paso(5, null, "Este caso de uso termina cuando el sistema confirma el registro.")));
		c.put("flujosAlternativos", List.of(
				desvio("2.1", "La persona aporta datos opcionales (paso 2).",
						"El sistema los conserva y continua en el paso 3.", 2)));
		c.put("flujosExcepcionales", List.of(
				desvio("E1", "Falta un dato obligatorio (paso 2).",
						"El sistema impide continuar. El flujo retorna al paso 2.", 2),
				desvio("E2", "El correo ya esta en uso (paso 3).",
						"El sistema informa del conflicto. El flujo retorna al paso 2.", 3)));
		c.put("postcondicionExito", "La persona consta como usuario y puede iniciar sesion.");
		c.put("postcondicionFracaso", "No queda ningun usuario registrado.");
		return c;
	}

	private List<Reparo> revisarCaso(Map<String, Object> c) {
		return SpecificationValidator.revisar(c, SpecificationGenerator.CASO_DE_USO);
	}

	@Test
	@DisplayName("Un caso de uso bien formado no produce reparos")
	void casoBuenoPasa() {
		assertThat(revisarCaso(casoBueno())).isEmpty();
	}

	@Test
	@DisplayName("El sistema no puede ser el actor principal")
	void sistemaNoEsActorPrincipal() {
		// El sistema es la frontera del caso de uso, no un participante.
		Map<String, Object> c = casoBueno();
		c.put("actorPrincipal", "El sistema");

		assertThat(revisarCaso(c)).anyMatch(r -> r.campo().equals("actorPrincipal") && r.grave());
	}

	@Test
	@DisplayName("Un flujo sin paso de comprobacion se senala")
	void sinComprobacion() {
		// Sin el, las excepciones no tienen de donde colgar y el caso de uso solo
		// describe el camino que sale bien.
		Map<String, Object> c = casoBueno();
		c.put("flujoPrincipal", List.of(
				paso(1, "Este caso de uso inicia cuando la persona solicita registrarse.",
						"El sistema presenta el formulario."),
				paso(2, "La persona envia los datos.", "El sistema registra al usuario."),
				paso(3, null, "Este caso de uso termina cuando el sistema confirma.")));

		assertThat(revisarCaso(c)).anyMatch(r -> r.motivo().contains("comprueba nada"));
	}

	@Test
	@DisplayName("Las decisiones de diseno en el flujo se senalan")
	void disenoEnElFlujo() {
		// Si el caso de uso menciona la base, las pruebas que salgan de el heredan
		// esa decision sin que nadie la haya tomado.
		Map<String, Object> c = casoBueno();
		c.put("flujoPrincipal", List.of(
				paso(1, "Este caso de uso inicia cuando la persona solicita registrarse.",
						"El sistema comprueba los datos."),
				paso(2, null, "El sistema guarda los datos en la base de datos."),
				paso(3, null, "Este caso de uso termina cuando el sistema confirma.")));

		assertThat(revisarCaso(c)).anyMatch(r -> r.motivo().contains("base de datos"));
	}

	@Test
	@DisplayName("Una excepcion que cuelga de un paso inexistente es grave")
	void excepcionSinPaso() {
		Map<String, Object> c = casoBueno();
		c.put("flujosExcepcionales", List.of(desvio("E1", "Falla algo (paso 9).", "Se corrige.", 9)));

		assertThat(revisarCaso(c))
				.anyMatch(r -> r.grave() && r.motivo().contains("no existe"));
	}

	@Test
	@DisplayName("Un caso de uso sin excepciones se senala")
	void sinExcepciones() {
		Map<String, Object> c = casoBueno();
		c.put("flujosExcepcionales", List.of());

		assertThat(revisarCaso(c)).anyMatch(r -> r.campo().equals("flujosExcepcionales"));
	}

	@Test
	@DisplayName("Faltar el flujo principal es grave: es lo que hace expandido al caso de uso")
	void sinFlujoPrincipal() {
		Map<String, Object> c = casoBueno();
		c.put("flujoPrincipal", List.of());

		assertThat(revisarCaso(c)).anyMatch(r -> r.campo().equals("flujoPrincipal") && r.grave());
	}

	// --- Historias de usuario ---

	private List<Reparo> revisarHistoria(String descripcion, String criterios) {
		return SpecificationValidator.revisar(
				Map.of("descripcion", descripcion, "criteriosDeAceptacion", criterios),
				SpecificationGenerator.HISTORIA);
	}

	private static final String CRITERIOS_COMPLETOS = """
			Caracteristica: Registro

			Escenario: Registro con datos completos
			Dado que aporto todos los datos
			Cuando confirmo
			Entonces el sistema me registra

			Escenario: Correo ya en uso
			Dado que el correo existe
			Cuando confirmo
			Entonces el sistema informa del conflicto
			""";

	@Test
	@DisplayName("Una historia bien formada no produce reparos")
	void historiaBuenaPasa() {
		assertThat(revisarHistoria(
				"Como persona interesada en usar la plataforma, quiero registrarme aportando mis "
						+ "datos, para poder acceder con mi propia cuenta.",
				CRITERIOS_COMPLETOS)).isEmpty();
	}

	@Test
	@DisplayName("Sin beneficio, la historia no puede priorizarse")
	void sinBeneficio() {
		assertThat(revisarHistoria("Como persona, quiero registrarme.", CRITERIOS_COMPLETOS))
				.anyMatch(r -> r.motivo().contains("no puede priorizarse"));
	}

	@Test
	@DisplayName("El rol de una historia no puede ser el sistema")
	void rolNoEsElSistema() {
		// Una historia expresa lo que quiere una persona, no lo que hace el sistema.
		assertThat(revisarHistoria(
				"Como el sistema, quiero registrar personas, para tener usuarios.",
				CRITERIOS_COMPLETOS))
				.anyMatch(r -> r.campo().equals("descripcion") && r.grave());
	}

	@Test
	@DisplayName("Una historia con un solo escenario no esta especificada")
	void unSoloEscenario() {
		assertThat(revisarHistoria("Como persona, quiero registrarme, para acceder.",
				"Escenario: exito\nDado a\nCuando b\nEntonces c"))
				.anyMatch(r -> r.campo().equals("criteriosDeAceptacion"));
	}

	@Test
	@DisplayName("Faltar los criterios de aceptacion es grave: la tabla 9 los exige")
	void sinCriterios() {
		assertThat(revisarHistoria("Como persona, quiero registrarme, para acceder.", ""))
				.anyMatch(r -> r.campo().equals("criteriosDeAceptacion") && r.grave());
	}

	@Test
	@DisplayName("Lo escrito a mano se comprueba igual que lo generado")
	void mismaVaraDeMedir() {
		// Lo escrito a mano no es mas fiable: solo tiene otro autor.
		Map<String, Object> c = casoBueno();
		c.put("actorPrincipal", "La plataforma");

		assertThat(revisarCaso(c)).anyMatch(Reparo::grave);
	}
}
