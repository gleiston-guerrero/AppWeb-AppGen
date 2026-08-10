package org.slcp.service.ingestion;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Lector de XML.
 *
 * <p>Cada requisito es un elemento, y sus campos pueden venir como atributos o
 * como elementos hijos. Se admiten ambos porque ambos se usan, y exigir uno
 * dejaria fuera la mitad de los documentos reales.</p>
 */
public final class XmlRequirementSource implements RequirementSource {

	private final ImportProfile perfil;

	public XmlRequirementSource(ImportProfile perfil) {
		this.perfil = perfil;
	}

	@Override
	public ExtractionReport extraer(Reader documento) throws IOException {
		ExtractionBuilder constructor = new ExtractionBuilder(perfil);
		String nombreElemento = perfil.ajuste("xml.item", "requisito");

		try {
			DocumentBuilderFactory factoria = DocumentBuilderFactory.newInstance();

			// Se desactiva la resolucion de entidades externas. Un documento de
			// requisitos llega de fuera, y una entidad externa permitiria que su autor
			// hiciera leer al servidor archivos que no le corresponden.
			factoria.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factoria.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factoria.setExpandEntityReferences(false);
			factoria.setXIncludeAware(false);

			NodeList elementos = factoria.newDocumentBuilder()
					.parse(new InputSource(documento))
					.getElementsByTagName(nombreElemento);

			for (int i = 0; i < elementos.getLength(); i++) {
				Element elemento = (Element) elementos.item(i);
				Map<String, String> campos = new LinkedHashMap<>();
				List<String> desconocidas = new ArrayList<>();

				NamedNodeMap atributos = elemento.getAttributes();
				for (int a = 0; a < atributos.getLength(); a++) {
					Node atributo = atributos.item(a);
					anotar(campos, desconocidas, atributo.getNodeName(), atributo.getNodeValue());
				}

				NodeList hijos = elemento.getChildNodes();
				for (int h = 0; h < hijos.getLength(); h++) {
					Node hijo = hijos.item(h);
					if (hijo.getNodeType() == Node.ELEMENT_NODE) {
						anotar(campos, desconocidas, hijo.getNodeName(), hijo.getTextContent());
					}
				}

				constructor.anadir(campos, desconocidas, i + 1);
			}

		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("El documento XML no se pudo analizar: " + e.getMessage(), e);
		}

		return constructor.construir();
	}

	private void anotar(Map<String, String> campos, List<String> desconocidas,
			String etiqueta, String valor) {

		String limpio = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
		if (limpio.isEmpty()) {
			return;
		}
		String campo = perfil.campoDe(etiqueta);
		if (campo.isEmpty()) {
			desconocidas.add(etiqueta);
		} else {
			campos.merge(campo, limpio, (a, n) -> a.equals(n) ? a : a + " " + n);
		}
	}
}
