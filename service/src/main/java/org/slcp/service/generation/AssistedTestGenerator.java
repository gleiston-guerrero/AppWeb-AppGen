package org.slcp.service.generation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slcp.service.domain.AiProvider;
import org.slcp.service.ingestion.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Genera pruebas con un modelo de lenguaje.
 *
 * <p>Se conecta en lugar del generador derivado cuando hay credencial
 * configurada. Si el servicio externo falla o tarda, se recurre al derivado: la
 * generacion asistida mejora el resultado y no puede ser condicion para
 * trabajar (ANA-06).</p>
 *
 * <p><strong>El limite es el mismo y aqui se comprueba, no se confia.</strong> El
 * modelo recibe la instruccion de no inventar magnitudes, pero una instruccion no
 * es una garantia: lo que devuelve se examina, y toda cifra que no estuviera en
 * el requisito se sustituye por un hueco. Una prueba con un umbral inventado pasa
 * o falla por un numero que nadie decidio, y lo hace en silencio.</p>
 */
public final class AssistedTestGenerator implements TestGenerator {

	private static final Logger log = LoggerFactory.getLogger(AssistedTestGenerator.class);

	/** Marca de lo que ha de rellenar una persona. */
	public static final String HUECO = DerivedTestGenerator.HUECO;

	private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)?");

	private final TestGenerator respaldo;
	private final HttpClient cliente;
	private final AiProvider proveedor;
	private final String url;
	private final String credencial;
	private final String modelo;

	/**
	 * La instruccion que se envia, propia de la funcion.
	 *
	 * <p>Se recibe en lugar de traerla dentro para que pueda editarse sin
	 * recompilar, y para que todas las APIs de una funcion reciban exactamente la
	 * misma: es la condicion que hace valida una comparacion.</p>
	 */
	private final String plantilla;

	public AssistedTestGenerator(TestGenerator respaldo, AiProvider proveedor, String url,
			String credencial, String modelo, String plantilla, Duration espera) {

		this.respaldo = respaldo;
		this.proveedor = proveedor;
		this.url = url;
		this.credencial = credencial;
		this.modelo = modelo;
		this.plantilla = plantilla == null || plantilla.isBlank()
				? PromptCatalog.porDefecto(org.slcp.service.domain.AiFeature.GENERATE_TESTS)
				: plantilla;
		this.cliente = HttpClient.newBuilder().connectTimeout(espera).build();
	}

	@Override
	public List<String> clases() {
		return respaldo.clases();
	}

	@Override
	public List<ArtifactProposal> generar(RequirementInput r, String clase) {
		try {
			List<ArtifactProposal> propuestas = pedirAlModelo(r, clase);

			if (propuestas.isEmpty()) {
				return respaldo.generar(r, clase);
			}
			return propuestas;

		} catch (Exception e) {
			// Se recurre al derivado y se deja constancia. Fallar del todo dejaria
			// sin generacion a quien solo queria una prueba.
			log.warn("La generacion asistida fallo para {}; se emplea la derivada: {}",
					r.readableId(), e.getMessage());
			return respaldo.generar(r, clase);
		}
	}

	// =================================================================

	private List<ArtifactProposal> pedirAlModelo(RequirementInput r, String clase) throws Exception {
		String peticion = cuerpoDe(r, clase);

		HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(direccion()))
				.header("content-type", "application/json")
				.timeout(Duration.ofSeconds(60))
				.POST(HttpRequest.BodyPublishers.ofString(peticion, StandardCharsets.UTF_8));

		// Cada servicio pide la credencial en una cabecera distinta. Enviarlas
		// todas seria entregar la clave a quien no la necesita.
		switch (proveedor) {
			case ANTHROPIC -> constructor
					.header("x-api-key", credencial)
					.header("anthropic-version", "2023-06-01");
			case GOOGLE -> constructor.header("x-goog-api-key", credencial);
			default -> constructor.header("authorization", "Bearer " + credencial);
		}

		HttpRequest solicitud = constructor.build();

		HttpResponse<String> respuesta = cliente.send(solicitud,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (respuesta.statusCode() != 200) {
			throw new IllegalStateException("el servicio respondio " + respuesta.statusCode());
		}

		return interpretar(respuesta.body(), r, clase);
	}

	/**
	 * Instruccion al modelo.
	 *
	 * <p>Se le dice que no invente magnitudes, y ademas se comprueba despues. La
	 * instruccion reduce el problema; la comprobacion lo cierra.</p>
	 */
	/**
	 * Direccion a la que se llama.
	 *
	 * <p>Google pone el modelo en la propia direccion; los demas, en el cuerpo. Es
	 * una diferencia de forma que no debe alcanzar al resto del codigo.</p>
	 */
	private String direccion() {
		if (proveedor == AiProvider.GOOGLE) {
			String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
			return base + "/" + modelo + ":generateContent";
		}
		return url;
	}

	private String cuerpoDe(RequirementInput r, String clase) {
		String instruccion = plantilla
				.replace(PromptCatalog.HUECO, HUECO)
				.replace(PromptCatalog.CLASE, clase)
				.replace(PromptCatalog.REQUISITO, descripcionDe(r));

		String texto = escapar(instruccion);

		return switch (proveedor) {
			case ANTHROPIC -> "{\"model\":\"" + escapar(modelo) + "\",\"max_tokens\":1500,"
					+ "\"messages\":[{\"role\":\"user\",\"content\":\"" + texto + "\"}]}";

			case GOOGLE -> "{\"contents\":[{\"parts\":[{\"text\":\"" + texto + "\"}]}]}";

			// OpenAI y cuanto habla como el.
			default -> "{\"model\":\"" + escapar(modelo) + "\",\"max_tokens\":1500,"
					+ "\"messages\":[{\"role\":\"user\",\"content\":\"" + texto + "\"}]}";
		};
	}

	/** El requisito, tal como se le presenta al modelo. */
	private String descripcionDe(RequirementInput r) {
		return """
				REQUISITO
				  Identificador: %s (%s)
				  Nombre: %s
				  Enunciado: %s
				  Criterio de verificacion: %s
				  Interesado o fuente: %s
				"""
				.formatted(r.etiqueta(), r.kind(), nombreDe(r), r.statement(),
						r.tieneCriterio() ? r.verification() : "no lo tiene",
						r.actor() == null ? "no declarado" : r.actor());
	}

	@SuppressWarnings("unchecked")
	private List<ArtifactProposal> interpretar(String cuerpo, RequirementInput r, String clase) {
		Object raiz = JsonParser.analizar(cuerpo);

		if (!(raiz instanceof java.util.Map<?, ?> objeto)) {
			return List.of();
		}

		String generado = textoDe(objeto).trim();
		if (generado.isEmpty()) {
			return List.of();
		}

		Revision revision = revisarMagnitudes(generado, r);

		return List.of(new ArtifactProposal(clase,
				"Asistida — " + clase.toLowerCase() + " de " + r.etiqueta(),
				revision.texto(),
				"GHERKIN",
				"Redactada por el modelo a partir del enunciado y del criterio del requisito"
						+ (revision.sustituidas() > 0
								? ". Se sustituyeron " + revision.sustituidas() + " cifras que no "
										+ "aparecian en el requisito: el modelo las propuso y la "
										+ "plataforma no admite magnitudes inventadas"
								: "")
						+ ". Es una propuesta y no una prueba comprobada: leala antes de aceptarla",
				revision.texto().contains(HUECO),
				List.of(r.readableId())));
	}

	/**
	 * Extrae el texto de la respuesta, que cada servicio devuelve a su manera.
	 *
	 * <p>Se leen las tres formas conocidas y se recorre la que aparezca. Suponer
	 * una sola dejaria la funcion inservible con dos de los tres proveedores, y el
	 * fallo se leeria como que el modelo no contesto.</p>
	 */
	private String textoDe(java.util.Map<?, ?> objeto) {
		// Anthropic: content -> [ { type: text, text } ]
		if (objeto.get("content") instanceof List<?> bloques) {
			StringBuilder texto = new StringBuilder();
			for (Object bloque : bloques) {
				if (bloque instanceof java.util.Map<?, ?> b && "text".equals(b.get("type"))) {
					texto.append(String.valueOf(b.get("text")));
				}
			}
			return texto.toString();
		}

		// OpenAI: choices -> [ { message: { content } } ]
		if (objeto.get("choices") instanceof List<?> opciones && !opciones.isEmpty()
				&& opciones.get(0) instanceof java.util.Map<?, ?> primera
				&& primera.get("message") instanceof java.util.Map<?, ?> mensaje) {
			return String.valueOf(mensaje.get("content"));
		}

		// Google: candidates -> [ { content: { parts: [ { text } ] } } ]
		if (objeto.get("candidates") instanceof List<?> candidatos && !candidatos.isEmpty()
				&& candidatos.get(0) instanceof java.util.Map<?, ?> primero
				&& primero.get("content") instanceof java.util.Map<?, ?> contenido
				&& contenido.get("parts") instanceof List<?> partes) {

			StringBuilder texto = new StringBuilder();
			for (Object parte : partes) {
				if (parte instanceof java.util.Map<?, ?> p) {
					texto.append(String.valueOf(p.get("text")));
				}
			}
			return texto.toString();
		}

		return "";
	}

	/** Texto revisado y cuantas cifras hubo que sustituir. */
	private record Revision(String texto, int sustituidas) {
	}

	/**
	 * Sustituye por huecos las cifras que el requisito no traia.
	 *
	 * <p>Se comparan los numeros del texto generado con los del requisito. Los que
	 * no estaban se sustituyen: el modelo puede haberlos elegido con buen criterio,
	 * pero elegir umbrales corresponde a quien responde del sistema, y una prueba
	 * que los trae ya puestos quita esa decision sin anunciarlo.</p>
	 *
	 * <p>Se respetan los numeros de un solo digito y los que forman parte de un
	 * identificador: casi siempre son enumeraciones del propio Gherkin y
	 * sustituirlos haria ilegible el resultado.</p>
	 */
	private Revision revisarMagnitudes(String generado, RequirementInput r) {
		Set<String> conocidos = new HashSet<>();
		Matcher origen = NUMERO.matcher(
				r.statement() + " " + (r.verification() == null ? "" : r.verification())
						+ " " + r.etiqueta());

		while (origen.find()) {
			conocidos.add(origen.group().replace(',', '.'));
		}

		StringBuilder salida = new StringBuilder();
		Matcher m = NUMERO.matcher(generado);
		int ultimo = 0;
		int sustituidas = 0;

		while (m.find()) {
			String cifra = m.group();
			String normal = cifra.replace(',', '.');

			boolean esConocida = conocidos.contains(normal);
			boolean esPequena = cifra.length() == 1;
			boolean enIdentificador = m.start() > 0
					&& Character.isLetter(generado.charAt(m.start() - 1));

			salida.append(generado, ultimo, m.start());

			if (esConocida || esPequena || enIdentificador) {
				salida.append(cifra);
			} else {
				salida.append(HUECO);
				sustituidas++;
			}
			ultimo = m.end();
		}
		salida.append(generado.substring(ultimo));

		return new Revision(salida.toString(), sustituidas);
	}

	private String nombreDe(RequirementInput r) {
		return r.name() == null || r.name().isBlank() ? r.etiqueta() : r.name();
	}

	private String escapar(String texto) {
		return texto.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
	}
}
