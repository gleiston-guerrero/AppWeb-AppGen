package org.slcp.service.generation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slcp.service.domain.AiProvider;
import org.slcp.service.ingestion.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Genera casos de uso expandidos e historias de usuario con un modelo.
 *
 * <p>No hay implementacion derivada de esta interfaz, y es a proposito: la
 * accion del actor no esta en ningun requisito, y una plantilla que la rellenara
 * inventaria la interaccion. Si no hay servicio configurado, la funcion se
 * ofrece apagada en lugar de dar un resultado peor haciendose pasar por el
 * bueno.</p>
 *
 * <p>Se pide al modelo que devuelva un documento estructurado y no prosa, para
 * poder guardarlo por campos y comprobar despues que no falte ninguno de los
 * obligatorios. Prosa libre obligaria a interpretarla, y ahi es donde se
 * pierden los campos sin que nadie se entere.</p>
 */
public final class AssistedSpecificationGenerator implements SpecificationGenerator {

	private static final Logger log =
			LoggerFactory.getLogger(AssistedSpecificationGenerator.class);

	/** Marca de lo que el modelo no pudo determinar. */
	public static final String HUECO = "[por decidir]";

	private final HttpClient cliente;
	private final AiProvider proveedor;
	private final String url;
	private final String credencial;
	private final String modelo;

	/** La instruccion, propia de la funcion y comun a todas las APIs. */
	private final String plantilla;

	public AssistedSpecificationGenerator(AiProvider proveedor, String url, String credencial,
			String modelo, String plantilla, Duration espera) {

		this.proveedor = proveedor;
		this.url = url;
		this.credencial = credencial;
		this.modelo = modelo;
		this.plantilla = plantilla == null || plantilla.isBlank()
				? PromptCatalog.porDefecto(org.slcp.service.domain.AiFeature.GENERATE_SPECS)
				: plantilla;
		this.cliente = HttpClient.newBuilder().connectTimeout(espera).build();
	}

	@Override
	public boolean disponible() {
		return credencial != null && !credencial.isBlank();
	}

	@Override
	public List<Resultado> generar(List<RequirementInput> requisitos, String kind) {
		if (requisitos.isEmpty()) {
			return List.of();
		}

		try {
			String respuesta = pedir(instruccionDe(requisitos, kind));
			return interpretar(respuesta, requisitos, kind);

		} catch (Exception e) {
			// No hay respaldo derivado: se dice que fallo. Devolver una plantilla
			// vacia haria creer que el modelo contesto algo pobre, cuando lo que
			// ocurrio es que no contesto.
			log.warn("La generacion de {} fallo: {}", kind, e.getMessage());
			throw new GenerationException(
					"El servicio de IA no respondio. Compruebe la conexion desde la configuracion: "
							+ "los casos de uso y las historias no pueden generarse sin el, porque la "
							+ "accion del actor no esta en ningun requisito");
		}
	}

	// =================================================================

	/**
	 * Instruccion al modelo.
	 *
	 * <p>Sale de la plantilla de la funcion, con sus marcas sustituidas. La misma
	 * para todas las APIs: con instrucciones distintas se compararian las
	 * instrucciones y no los modelos.</p>
	 */
	private String instruccionDe(List<RequirementInput> requisitos, String kind) {
		StringBuilder descripcion = new StringBuilder("REQUISITOS DE LOS QUE HA DE SALIR\n");

		for (RequirementInput r : requisitos) {
			descripcion.append("  ").append(r.etiqueta()).append(" (").append(r.kind())
					.append("): ").append(r.statement()).append('\n');

			if (r.tieneCriterio()) {
				descripcion.append("    Criterio de verificacion: ").append(r.verification())
						.append('\n');
			}
		}

		return plantilla
				.replace(PromptCatalog.HUECO, HUECO)
				.replace(PromptCatalog.CLASE, kind)
				.replace(PromptCatalog.REQUISITO, descripcion.toString());
	}



	private String pedir(String instruccion) throws Exception {
		String cuerpo = switch (proveedor) {
			case ANTHROPIC -> "{\"model\":\"" + escapar(modelo) + "\",\"max_tokens\":3000,"
					+ "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapar(instruccion) + "\"}]}";
			case GOOGLE -> "{\"contents\":[{\"parts\":[{\"text\":\"" + escapar(instruccion) + "\"}]}]}";
			default -> "{\"model\":\"" + escapar(modelo) + "\",\"max_tokens\":3000,"
					+ "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapar(instruccion) + "\"}]}";
		};

		HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(direccion()))
				.header("content-type", "application/json")
				.timeout(Duration.ofSeconds(120))
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));

		switch (proveedor) {
			case ANTHROPIC -> constructor
					.header("x-api-key", credencial)
					.header("anthropic-version", "2023-06-01");
			case GOOGLE -> constructor.header("x-goog-api-key", credencial);
			default -> constructor.header("authorization", "Bearer " + credencial);
		}

		HttpResponse<String> respuesta = cliente.send(constructor.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (respuesta.statusCode() != 200) {
			throw new IllegalStateException("el servicio respondio " + respuesta.statusCode());
		}
		return respuesta.body();
	}

	private String direccion() {
		if (proveedor == AiProvider.GOOGLE) {
			String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
			return base + "/" + modelo + ":generateContent";
		}
		return url;
	}

	private List<Resultado> interpretar(String cuerpo, List<RequirementInput> requisitos,
			String kind) {

		Object raiz = JsonParser.analizar(cuerpo);
		if (!(raiz instanceof Map<?, ?> objeto)) {
			throw new IllegalStateException("la respuesta no es un objeto");
		}

		String generado = textoDe(objeto).trim();
		String documento = soloElObjeto(generado);

		if (documento.isBlank()) {
			throw new IllegalStateException("el modelo no devolvio un documento estructurado");
		}

		// Se comprueba que sea interpretable antes de guardarlo: un documento que no
		// puede leerse se guardaria igual y fallaria al mostrarlo, lejos de aqui.
		Object campos = JsonParser.analizar(documento);
		if (!(campos instanceof Map<?, ?> mapa)) {
			throw new IllegalStateException("el documento devuelto no es un objeto");
		}

		List<String> huecos = huecosDe(mapa);

		return List.of(new Resultado(kind, nombreDe(mapa, kind, requisitos), documento,
				"Redactado por " + proveedor.getEtiqueta() + " a partir de "
						+ requisitos.stream().map(RequirementInput::etiqueta).toList()
						+ ". Es una propuesta: la accion del actor no esta en ningun requisito y "
						+ "procede de lo que el modelo infirio",
				huecos,
				requisitos.stream().map(RequirementInput::readableId).toList()));
	}

	/**
	 * Recoge los huecos que el modelo dejo declarados.
	 *
	 * <p>Se recorre el documento entero porque pueden aparecer en cualquier campo,
	 * incluidos los pasos del flujo.</p>
	 */
	private List<String> huecosDe(Object valor) {
		List<String> encontrados = new ArrayList<>();
		recorrer(valor, "", encontrados);
		return encontrados;
	}

	private void recorrer(Object valor, String camino, List<String> encontrados) {
		if (valor instanceof Map<?, ?> mapa) {
			mapa.forEach((k, v) -> recorrer(v, camino.isEmpty() ? String.valueOf(k)
					: camino + "." + k, encontrados));

		} else if (valor instanceof List<?> lista) {
			for (int i = 0; i < lista.size(); i++) {
				recorrer(lista.get(i), camino + "[" + i + "]", encontrados);
			}

		} else if (valor instanceof String texto && texto.contains(HUECO)) {
			encontrados.add(camino);
		}
	}

	private String nombreDe(Map<?, ?> mapa, String kind, List<RequirementInput> requisitos) {
		Object nombre = CASO_DE_USO.equals(kind) ? mapa.get("nombre") : mapa.get("descripcion");

		if (nombre instanceof String texto && !texto.isBlank()) {
			return texto.length() > 200 ? texto.substring(0, 199) : texto;
		}
		return (CASO_DE_USO.equals(kind) ? "Caso de uso de " : "Historia de ")
				+ requisitos.get(0).etiqueta();
	}

	/**
	 * Extrae el objeto JSON de la respuesta.
	 *
	 * <p>Los modelos suelen envolverlo en un bloque de codigo o anadir una frase
	 * antes, pese a pedirles que no lo hagan. Recortar por las llaves es mas fiable
	 * que confiar en que obedezcan.</p>
	 */
	private String soloElObjeto(String texto) {
		int abre = texto.indexOf('{');
		int cierra = texto.lastIndexOf('}');

		return abre >= 0 && cierra > abre ? texto.substring(abre, cierra + 1) : "";
	}

	private String textoDe(Map<?, ?> objeto) {
		if (objeto.get("content") instanceof List<?> bloques) {
			StringBuilder texto = new StringBuilder();
			for (Object bloque : bloques) {
				if (bloque instanceof Map<?, ?> b && "text".equals(b.get("type"))) {
					texto.append(String.valueOf(b.get("text")));
				}
			}
			return texto.toString();
		}

		if (objeto.get("choices") instanceof List<?> opciones && !opciones.isEmpty()
				&& opciones.get(0) instanceof Map<?, ?> primera
				&& primera.get("message") instanceof Map<?, ?> mensaje) {
			return String.valueOf(mensaje.get("content"));
		}

		if (objeto.get("candidates") instanceof List<?> candidatos && !candidatos.isEmpty()
				&& candidatos.get(0) instanceof Map<?, ?> primero
				&& primero.get("content") instanceof Map<?, ?> contenido
				&& contenido.get("parts") instanceof List<?> partes) {

			StringBuilder texto = new StringBuilder();
			for (Object parte : partes) {
				if (parte instanceof Map<?, ?> p) {
					texto.append(String.valueOf(p.get("text")));
				}
			}
			return texto.toString();
		}
		return "";
	}

	private String escapar(String texto) {
		return texto.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
	}
}
