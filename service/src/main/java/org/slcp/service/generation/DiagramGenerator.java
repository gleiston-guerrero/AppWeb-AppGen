package org.slcp.service.generation;

import java.util.List;

/**
 * Genera diagramas a partir de los requisitos.
 *
 * <p>Los diagramas se generan del conjunto y no de un requisito suelto: un
 * diagrama de casos de uso con un solo caso no dice nada, y lo que se quiere
 * ver es como se relacionan entre si.</p>
 */
public interface DiagramGenerator {

	List<String> clases();

	/**
	 * Genera un diagrama del conjunto de requisitos.
	 *
	 * @param requisitos los requisitos aprobados del proyecto
	 * @param clase      que clase de diagrama se pide
	 */
	List<ArtifactProposal> generar(List<RequirementInput> requisitos, String clase);
}
