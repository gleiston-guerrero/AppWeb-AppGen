package org.slcp.service.ingestion;

import java.util.List;

/**
 * Propone criterios de verificacion.
 *
 * <p>Se declara como interfaz porque ANA-06 exige que la plataforma funcione con
 * el analisis asistido desactivado. La implementacion determinista es la de por
 * defecto; una basada en generacion asistida se conecta aqui sin que nada mas
 * cambie, y sin que su ausencia deje la funcion inservible.</p>
 */
public interface CriterionSuggester {

	/**
	 * Una propuesta concreta.
	 *
	 * @param texto        la redaccion propuesta
	 * @param fundamento   de donde sale, para que quien revisa pueda juzgarla
	 * @param exigeDecision si contiene huecos que una persona debe rellenar
	 */
	record Suggestion(String texto, String fundamento, boolean exigeDecision) {
	}

	/**
	 * Propone varias redacciones, nunca una sola (ANA-20).
	 *
	 * @param enunciado        texto del requisito
	 * @param requiereMagnitud si el requisito exige una cifra que la plataforma
	 *                         no debe inventar
	 */
	List<Suggestion> proponer(String enunciado, boolean requiereMagnitud);
}
