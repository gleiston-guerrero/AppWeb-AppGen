package org.slcp.service.generation;

import java.util.List;

/**
 * Propuesta de artefacto, antes de guardarse.
 *
 * <p>Nace propuesta y nunca aceptada: lo que sale de aqui es una redaccion que
 * alguien ha de juzgar. Que la haya escrito la plataforma o un modelo no la
 * convierte en correcta, y aceptarla sola quitaria a quien responde del sistema
 * la unica ocasion de leerla.</p>
 *
 * @param subkind        que clase de prueba o diagrama es
 * @param title          nombre legible
 * @param content        el texto: codigo de prueba o diagrama en Mermaid
 * @param format         en que esta escrito
 * @param rationale      de donde sale, para poder juzgarla
 * @param needsDecision  si contiene huecos que una persona debe rellenar
 * @param requirements   identificadores de los requisitos que cubre
 */
public record ArtifactProposal(
		String subkind,
		String title,
		String content,
		String format,
		String rationale,
		boolean needsDecision,
		List<String> requirements) {
}
