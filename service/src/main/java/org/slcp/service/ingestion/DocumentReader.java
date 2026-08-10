package org.slcp.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Convierte un documento a un arbol comun, sea cual sea su formato.
 *
 * <p>Con un solo arbol, la extraccion es la misma para JSON, YAML, XML y valores
 * separados, y solo cambia la conversion previa. Sin este paso habria cuatro
 * extractores casi iguales, y cuatro sitios donde corregir cada defecto.</p>
 */
public final class DocumentReader {

	private final ObjectMapper json;

	public DocumentReader(ObjectMapper json) {
		this.json = json;
	}

	/** Convierte segun la naturaleza declarada en el perfil. */
	public JsonNode leer(String contenido, String tipo) throws IOException {
		return switch (tipo) {
			case "json" -> json.readTree(contenido);
			case "yaml" -> deYaml(contenido);
			case "xml" -> deXml(contenido);
			case "csv" -> deCsv(contenido);
			default -> throw new IllegalArgumentException("Formato no reconocido: " + tipo);
		};

	}

	/**
	 * Convierte YAML a arbol.
	 *
	 * <p>Se emplea SnakeYAML directamente y no el complemento de Jackson: el
	 * primero ya viene con Spring Boot, que lo usa para su propia configuracion,
	 * mientras que el segundo seria una dependencia mas que anadir por algo que
	 * ya esta resuelto.</p>
	 *
	 * <p>La carga es la restringida a tipos simples. La carga general de YAML
	 * puede instanciar clases indicadas en el propio documento, y aqui el
	 * documento lo sube cualquiera.</p>
	 */
	private JsonNode deYaml(String contenido) {
		LoaderOptions opciones = new LoaderOptions();
		opciones.setAllowDuplicateKeys(false);

		Object cargado = new Yaml(new SafeConstructor(opciones)).load(contenido);
		return json.valueToTree(cargado);
	}

	// =================================================================
	// XML
	// =================================================================

	/**
	 * Convierte XML a arbol.
	 *
	 * <p>Los atributos y los elementos hijos se tratan igual: que un dato viaje
	 * como atributo o como elemento es una decision de estilo de quien escribio
	 * el documento, no una diferencia de significado.</p>
	 */
	private JsonNode deXml(String contenido) throws IOException {
		try {
			DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();

			// Se desactiva el procesamiento de entidades externas. Sin esto, un
			// documento subido por cualquiera podria hacer que el servidor leyese
			// archivos suyos o abriese conexiones de red al analizarlo.
			fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
			fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			fabrica.setXIncludeAware(false);
			fabrica.setExpandEntityReferences(false);

			Document documento = fabrica.newDocumentBuilder()
					.parse(new InputSource(new StringReader(contenido)));
			documento.getDocumentElement().normalize();

			ArrayNode lista = json.createArrayNode();
			for (Element elemento : hijosDirectos(documento.getDocumentElement())) {
				lista.add(objetoDe(elemento));
			}
			return lista;

		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("El documento XML no se pudo leer: " + e.getMessage(), e);
		}
	}

	private ObjectNode objetoDe(Element elemento) {
		ObjectNode objeto = json.createObjectNode();

		NamedNodeMap atributos = elemento.getAttributes();
		for (int i = 0; i < atributos.getLength(); i++) {
			Node atributo = atributos.item(i);
			objeto.put(atributo.getNodeName(), atributo.getNodeValue().trim());
		}

		for (Element hijo : hijosDirectos(elemento)) {
			String texto = textoDirecto(hijo);
			if (!hijosDirectos(hijo).isEmpty()) {
				objeto.set(hijo.getTagName(), objetoDe(hijo));
			} else if (!texto.isBlank()) {
				objeto.put(hijo.getTagName(), texto);
			}
		}
		return objeto;
	}

	private List<Element> hijosDirectos(Element padre) {
		List<Element> hijos = new ArrayList<>();
		NodeList nodos = padre.getChildNodes();
		for (int i = 0; i < nodos.getLength(); i++) {
			if (nodos.item(i) instanceof Element e) {
				hijos.add(e);
			}
		}
		return hijos;
	}

	private String textoDirecto(Element elemento) {
		StringBuilder texto = new StringBuilder();
		NodeList nodos = elemento.getChildNodes();
		for (int i = 0; i < nodos.getLength(); i++) {
			Node nodo = nodos.item(i);
			if (nodo.getNodeType() == Node.TEXT_NODE || nodo.getNodeType() == Node.CDATA_SECTION_NODE) {
				texto.append(nodo.getNodeValue());
			}
		}
		return texto.toString().trim().replaceAll("\\s+", " ");
	}

	// =================================================================
	// Valores separados por comas
	// =================================================================

	/**
	 * Convierte un archivo de valores separados a arbol.
	 *
	 * <p>La primera fila son los nombres de columna. Se admiten valores entre
	 * comillas con comas dentro, comillas dobladas para escapar una comilla, y
	 * saltos de linea dentro de un valor: sin ello, cualquier requisito cuyo
	 * enunciado contenga una coma partiria el archivo en silencio, y ese es el
	 * defecto mas frecuente de los lectores improvisados.</p>
	 */
	private JsonNode deCsv(String contenido) throws IOException {
		List<List<String>> filas = analizarCsv(contenido);
		if (filas.isEmpty()) {
			return json.createArrayNode();
		}

		List<String> cabecera = filas.get(0);
		ArrayNode lista = json.createArrayNode();

		for (int f = 1; f < filas.size(); f++) {
			List<String> fila = filas.get(f);
			if (fila.stream().allMatch(String::isBlank)) {
				continue;
			}
			ObjectNode objeto = json.createObjectNode();
			for (int c = 0; c < cabecera.size() && c < fila.size(); c++) {
				String valor = fila.get(c).trim();
				if (!valor.isEmpty()) {
					objeto.put(cabecera.get(c).trim(), valor);
				}
			}
			lista.add(objeto);
		}
		return lista;
	}

	/** Analiza el contenido conforme a la convencion de RFC 4180. */
	static List<List<String>> analizarCsv(String contenido) {
		List<List<String>> filas = new ArrayList<>();
		List<String> fila = new ArrayList<>();
		StringBuilder campo = new StringBuilder();
		boolean entreComillas = false;

		for (int i = 0; i < contenido.length(); i++) {
			char c = contenido.charAt(i);

			if (entreComillas) {
				if (c == '"') {
					// Dos comillas seguidas representan una comilla literal.
					if (i + 1 < contenido.length() && contenido.charAt(i + 1) == '"') {
						campo.append('"');
						i++;
					} else {
						entreComillas = false;
					}
				} else {
					campo.append(c);
				}
				continue;
			}

			switch (c) {
				case '"' -> entreComillas = true;
				case ',' -> {
					fila.add(campo.toString());
					campo.setLength(0);
				}
				case '\r' -> {
					// Se ignora: el salto lo marca el avance de linea.
				}
				case '\n' -> {
					fila.add(campo.toString());
					campo.setLength(0);
					filas.add(fila);
					fila = new ArrayList<>();
				}
				default -> campo.append(c);
			}
		}

		if (campo.length() > 0 || !fila.isEmpty()) {
			fila.add(campo.toString());
			filas.add(fila);
		}
		return filas;
	}
}
