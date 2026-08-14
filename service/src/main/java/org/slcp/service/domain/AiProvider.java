package org.slcp.service.domain;

/**
 * Proveedores de generacion asistida admitidos.
 *
 * <p>Cada uno trae su direccion y su modelo habituales para que quien configure
 * no tenga que buscarlos, pero ambos pueden cambiarse: los modelos se renuevan
 * mas deprisa que esta plataforma, y fijarlos aqui obligaria a tocar el codigo
 * cada vez que salga uno nuevo.</p>
 */
public enum AiProvider {

	ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1/messages",
			"claude-sonnet-4-6", "https://console.anthropic.com/settings/keys"),

	OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions",
			"gpt-4o", "https://platform.openai.com/api-keys"),

	/**
	 * DeepSeek.
	 *
	 * <p>Habla como el servicio de OpenAI, de modo que emplea su mismo adaptador:
	 * la credencial va en la cabecera de autorizacion y el cuerpo tiene la misma
	 * forma. Se declara aparte y no como "compatible" porque quien lo elija no
	 * tiene por que saber a quien se parece, y asi recibe su direccion y su modelo
	 * sin buscarlos.</p>
	 */
	DEEPSEEK("DeepSeek", "https://api.deepseek.com/chat/completions",
			"deepseek-chat", "https://platform.deepseek.com/api_keys"),

	GOOGLE("Google (Gemini)",
			"https://generativelanguage.googleapis.com/v1beta/models",
			"gemini-2.0-flash", "https://aistudio.google.com/apikey"),

	/**
	 * Cualquier servicio que hable como el de OpenAI.
	 *
	 * <p>Es lo que permite emplear un modelo propio o local sin que la plataforma
	 * tenga que conocerlo: muchos servicios exponen esa misma forma.</p>
	 */
	COMPATIBLE("Compatible con OpenAI (propio o local)", "", "", "");

	private final String etiqueta;
	private final String direccionPorDefecto;
	private final String modeloPorDefecto;
	private final String dondeConseguirLaClave;

	AiProvider(String etiqueta, String direccionPorDefecto, String modeloPorDefecto,
			String dondeConseguirLaClave) {

		this.etiqueta = etiqueta;
		this.direccionPorDefecto = direccionPorDefecto;
		this.modeloPorDefecto = modeloPorDefecto;
		this.dondeConseguirLaClave = dondeConseguirLaClave;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	public String getDireccionPorDefecto() {
		return direccionPorDefecto;
	}

	public String getModeloPorDefecto() {
		return modeloPorDefecto;
	}

	public String getDondeConseguirLaClave() {
		return dondeConseguirLaClave;
	}
}
