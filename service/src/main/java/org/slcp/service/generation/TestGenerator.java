package org.slcp.service.generation;

import java.util.List;

/**
 * Genera pruebas a partir de los requisitos.
 *
 * <p>Se declara como interfaz por la misma razon que el sugeridor de criterios:
 * la plataforma ha de funcionar con la generacion asistida desactivada. La
 * implementacion derivada es la de por defecto, y una asistida se conecta aqui
 * sin que nada mas cambie.</p>
 *
 * <p>El limite es el mismo de siempre y no depende de quien genere: lo que sale
 * puede proponer lo observable, y nunca inventar magnitudes que el requisito no
 * traiga (ANA-18). Una prueba con un umbral inventado pasa o falla por una cifra
 * que nadie decidio.</p>
 */
public interface TestGenerator {

	/** Clases de prueba que este generador sabe producir. */
	List<String> clases();

	/**
	 * Genera pruebas para un requisito.
	 *
	 * @param requisito el requisito aprobado
	 * @param clase     que clase de prueba se pide, de las que declara
	 */
	List<ArtifactProposal> generar(RequirementInput requisito, String clase);
}
