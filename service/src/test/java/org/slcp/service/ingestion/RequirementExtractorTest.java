package org.slcp.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo del extractor dirigido por perfil.
 *
 * <p>Las entradas son minimas y estan escritas aqui, no leidas de un archivo:
 * una prueba que depende de un documento externo mide dos cosas a la vez y no
 * dice cual fallo.</p>
 */
class RequirementExtractorTest {

	private static final String PERFIL = """
			profile.id     = prueba
			block.begin    = \\begin{atributos}
			block.end      = \\end{atributos}
			id.pattern     = \\\\begin\\{atributos\\}\\{([^}]+)\\}
			row.separator  = &
			row.terminator = \\\\
			field.Identificador            = id
			field.Nombre                   = name
			field.Descripcion              = description
			field.Criterio de verificacion = verification
			field.Criterio de aceptacion   = verification
			expected = id, description, verification
			""";

	private ImportProfile perfil() throws Exception {
		return ImportProfile.cargar(new StringReader(PERFIL));
	}

	private ExtractionReport extraer(String documento) throws Exception {
		return new RequirementExtractor(perfil()).extraer(new StringReader(documento));
	}

	@Test
	@DisplayName("Extrae un requisito completo con todos sus campos")
	void requisitoCompleto() throws Exception {
		ExtractionReport informe = extraer("""
				\\begin{atributos}{RF-01}{Registrar mascota.}
				Identificador & RF-01 \\\\
				Nombre & Registrar mascota \\\\
				Descripcion & El sistema debera permitir registrar una mascota. \\\\
				Criterio de verificacion & Registrar y comprobar que se almacena. \\\\
				\\end{atributos}
				""");

		assertThat(informe.total()).isEqualTo(1);
		assertThat(informe.completos()).isEqualTo(1);

		ParsedRequirement r = informe.requirements().get(0);
		assertThat(r.sourceId()).isEqualTo("RF-01");
		assertThat(r.get("name")).isEqualTo("Registrar mascota");
		assertThat(r.get("description")).contains("registrar una mascota");
		assertThat(r.completo()).isTrue();
	}

	@Test
	@DisplayName("Lo ausente se reporta, no se inventa")
	void ausenciasReportadas() throws Exception {
		ExtractionReport informe = extraer("""
				\\begin{atributos}{RF-02}{Sin criterio.}
				Identificador & RF-02 \\\\
				Descripcion & Hace algo. \\\\
				\\end{atributos}
				""");

		ParsedRequirement r = informe.requirements().get(0);
		assertThat(r.completo()).isFalse();
		assertThat(r.missing()).containsExactly("verification");
		assertThat(r.get("verification")).isEmpty();
		assertThat(informe.missingByField()).containsEntry("verification", 1);
	}

	@Test
	@DisplayName("Dos etiquetas distintas pueden dar el mismo campo canonico")
	void sinonimosDeEtiqueta() throws Exception {
		ExtractionReport informe = extraer("""
				\\begin{atributos}{HU-01}{Historia.}
				Identificador & HU-01 \\\\
				Descripcion & Como propietario quiero registrar mi mascota. \\\\
				Criterio de aceptacion & CA-01. \\\\
				\\end{atributos}
				""");

		assertThat(informe.requirements().get(0).completo()).isTrue();
		assertThat(informe.requirements().get(0).get("verification")).contains("CA-01");
	}

	@Test
	@DisplayName("La comparacion de etiquetas prescinde de acentos y mayusculas")
	void etiquetasConAcento() throws Exception {
		ExtractionReport informe = extraer("""
				\\begin{atributos}{RF-03}{Con acentos.}
				Identificador & RF-03 \\\\
				DESCRIPCIÓN & Hace algo. \\\\
				Criterio de Verificación & Se comprueba. \\\\
				\\end{atributos}
				""");

		assertThat(informe.unknownLabels()).isEmpty();
		assertThat(informe.requirements().get(0).completo()).isTrue();
	}

	@Test
	@DisplayName("Una etiqueta no declarada se reporta en lugar de descartarse en silencio")
	void etiquetaDesconocida() throws Exception {
		ExtractionReport informe = extraer("""
				\\begin{atributos}{RF-04}{Con campo ajeno.}
				Identificador & RF-04 \\\\
				Descripcion & Hace algo. \\\\
				Criterio de verificacion & Se comprueba. \\\\
				Prioridad segun el comite & Alta \\\\
				\\end{atributos}
				""");

		assertThat(informe.unknownLabels()).contains("Prioridad segun el comite");
		// El requisito sigue siendo utilizable: una etiqueta ajena no lo invalida.
		assertThat(informe.requirements().get(0).completo()).isTrue();
	}

	@Test
	@DisplayName("Un bloque comentado no es un requisito")
	void bloqueComentado() throws Exception {
		ExtractionReport informe = extraer("""
				% \\begin{atributos}{RF-99}{Ejemplo en un comentario.}
				% Identificador & RF-99 \\\\
				% \\end{atributos}
				\\begin{atributos}{RF-05}{El de verdad.}
				Identificador & RF-05 \\\\
				Descripcion & Hace algo. \\\\
				Criterio de verificacion & Se comprueba. \\\\
				\\end{atributos}
				""");

		assertThat(informe.total()).isEqualTo(1);
		assertThat(informe.requirements().get(0).sourceId()).isEqualTo("RF-05");
	}

	@Test
	@DisplayName("Los identificadores repetidos se detectan")
	void duplicados() throws Exception {
		String bloque = """
				\\begin{atributos}{RF-06}{Uno.}
				Identificador & RF-06 \\\\
				Descripcion & Hace algo. \\\\
				Criterio de verificacion & Se comprueba. \\\\
				\\end{atributos}
				""";

		ExtractionReport informe = extraer(bloque + bloque);

		assertThat(informe.total()).isEqualTo(2);
		assertThat(informe.duplicateIds()).containsExactly("RF-06");
	}

	@Test
	@DisplayName("Se conserva la linea de origen, para poder volver al documento")
	void lineaDeOrigen() throws Exception {
		ExtractionReport informe = extraer("""
				Texto suelto.

				\\begin{atributos}{RF-07}{Tercera linea.}
				Identificador & RF-07 \\\\
				\\end{atributos}
				""");

		assertThat(informe.requirements().get(0).sourceLine()).isEqualTo(3);
	}

	@Test
	@DisplayName("Las lineas sin separador no rompen la lectura")
	void lineasDeFormato() throws Exception {
		ExtractionReport informe = extraer("""
				\\begin{atributos}{RF-08}{Con ruido.}
				\\hline
				Identificador & RF-08 \\\\
				\\rowcolor{gray}
				Descripcion & Hace algo. \\\\
				Criterio de verificacion & Se comprueba. \\\\
				\\end{atributos}
				""");

		assertThat(informe.requirements().get(0).completo()).isTrue();
	}

	@Test
	@DisplayName("Un perfil sin las directivas obligatorias se rechaza al cargarse")
	void perfilIncompleto() {
		assertThatThrownBy(() -> ImportProfile.cargar(new StringReader("profile.id = x")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("block.begin");
	}

	@Test
	@DisplayName("Un documento sin bloques produce un informe vacio, no un fallo")
	void documentoSinRequisitos() throws Exception {
		ExtractionReport informe = extraer("Un documento cualquiera sin requisitos.");

		assertThat(informe.total()).isZero();
		assertThat(informe.missingByField()).isEmpty();
	}
}
