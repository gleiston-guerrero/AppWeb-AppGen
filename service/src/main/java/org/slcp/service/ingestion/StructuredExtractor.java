package org.slcp.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee documentos con estructura propia: JSON, YAML, XML y valores separados.
 *
 * <p>El otro lector, {@link RequirementExtractor}, trabaja por lineas y
 * etiquetas. Sirve para LaTeX, Markdown y texto plano, y no sirve para estos
 * cuatro: JSON anida, XML usa etiquetas con atributos y los valores separados
 * ponen un requisito por fila. Leerlos por lineas funcionaria con un archivo y
 * fallaria con el siguiente, que es peor que no admitirlos.</p>
 *
 * <p>Aqui se emplean analizadores de verdad. Lo que sigue siendo dato es la
 * correspondencia entre las claves del documento y los campos canonicos, de
 * modo que un JSON con otras claves se resuelve con otro perfil y no con otro
 * codigo.</p>
 */
public final class StructuredExtractor {

    private final ImportProfile perfil;
    private final ObjectMapper mapper;

    public StructuredExtractor(ImportProfile perfil, ObjectMapper mapper) {
        this.perfil = perfil;
        this.mapper = mapper;
    }

    /**
     * Lee el documento completo.
     *
     * @param documento contenido, ya convertido a arbol por quien corresponda
     */
    public ExtractionReport extraer(JsonNode raiz) {
        List<ParsedRequirement> requisitos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Set<String> duplicados = new LinkedHashSet<>();
        Set<String> desconocidas = new LinkedHashSet<>();
        Map<String, Integer> ausenciasPorCampo = new LinkedHashMap<>();

        JsonNode lista = localizarLista(raiz);
        int posicion = 0;

        for (JsonNode nodo : lista) {
            posicion++;
            Map<String, String> campos = new LinkedHashMap<>();
            List<String> raras = new ArrayList<>();

            Iterator<Map.Entry<String, JsonNode>> entradas = nodo.fields();
            while (entradas.hasNext()) {
                Map.Entry<String, JsonNode> entrada = entradas.next();
                String clave = entrada.getKey();
                String valor = textoDe(entrada.getValue());

                if (valor.isBlank()) {
                    continue;
                }

                String campo = perfil.campoDe(clave);
                if (campo.isEmpty()) {
                    raras.add(clave);
                    continue;
                }
                campos.merge(campo, valor,
                        (anterior, nuevo) -> anterior.equals(nuevo) ? anterior : anterior + " " + nuevo);
            }

            List<String> ausentes = ausentesDe(campos);
            for (String ausente : ausentes) {
                ausenciasPorCampo.merge(ausente, 1, Integer::sum);
            }

            String id = campos.getOrDefault("id", "");
            if (!id.isEmpty() && !vistos.add(id)) {
                duplicados.add(id);
            }
            desconocidas.addAll(raras);

            requisitos.add(new ParsedRequirement(id, campos, raras, ausentes, posicion));
        }

        return new ExtractionReport(perfil.getId(), requisitos, new ArrayList<>(duplicados),
                ausenciasPorCampo, new ArrayList<>(desconocidas));
    }

    /** Lee y convierte en un solo paso. */
    public ExtractionReport extraer(Reader documento) throws IOException {
        return extraer(mapper.readTree(documento));
    }

    /**
     * Encuentra la lista de requisitos dentro del documento.
     *
     * <p>Se admite tanto un arreglo en la raiz como un objeto que lo contenga
     * bajo la clave declarada en el perfil. Exigir una sola forma obligaria a
     * reescribir documentos que ya existen solo por como envuelven sus datos.</p>
     */
    private JsonNode localizarLista(JsonNode raiz) {
        if (raiz == null || raiz.isMissingNode()) {
            return mapper.createArrayNode();
        }
        if (raiz.isArray()) {
            return raiz;
        }

        String ruta = perfil.getListPath();
        if (!ruta.isEmpty()) {
            JsonNode nodo = raiz;
            for (String tramo : ruta.split("\\.")) {
                nodo = nodo.path(tramo);
            }
            if (nodo.isArray()) {
                return nodo;
            }
            // Un solo requisito, sin envolver en arreglo.
            if (nodo.isObject()) {
                return mapper.createArrayNode().add(nodo);
            }
        }

        // Sin ruta declarada, el primer arreglo de objetos que aparezca.
        Iterator<JsonNode> hijos = raiz.elements();
        while (hijos.hasNext()) {
            JsonNode hijo = hijos.next();
            if (hijo.isArray() && hijo.size() > 0 && hijo.get(0).isObject()) {
                return hijo;
            }
        }
        return mapper.createArrayNode();
    }

    /**
     * Texto de un valor.
     *
     * <p>Los arreglos se unen con coma: un campo con varios valores --- actores,
     * entradas --- es informacion del documento, y quedarse solo con el primero
     * la perderia en silencio.</p>
     */
    private String textoDe(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) {
            return "";
        }
        if (nodo.isArray()) {
            List<String> partes = new ArrayList<>();
            nodo.forEach(hijo -> {
                String t = textoDe(hijo);
                if (!t.isBlank()) {
                    partes.add(t);
                }
            });
            return String.join(", ", partes);
        }
        if (nodo.isObject()) {
            // Un objeto anidado se aplana a sus valores, sin perder nada.
            List<String> partes = new ArrayList<>();
            nodo.fields().forEachRemaining(e -> {
                String t = textoDe(e.getValue());
                if (!t.isBlank()) {
                    partes.add(e.getKey() + ": " + t);
                }
            });
            return String.join("; ", partes);
        }
        return nodo.asText("").trim();
    }

    private List<String> ausentesDe(Map<String, String> campos) {
        List<String> ausentes = new ArrayList<>();
        for (String esperado : perfil.getExpected()) {
            String valor = campos.get(esperado);
            if (valor == null || valor.isBlank()) {
                ausentes.add(esperado);
            }
        }
        return ausentes;
    }
}
