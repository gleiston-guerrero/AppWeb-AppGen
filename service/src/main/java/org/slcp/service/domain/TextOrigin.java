package org.slcp.service.domain;

/**
 * Procedencia de un texto de requisito.
 *
 * <p>Realiza ANA-16. Sin este dato, una revision posterior no puede distinguir
 * que enunciados escribio una persona y cuales proceden de una propuesta
 * aceptada, que es justo lo que hace falta para medir la deriva de ANA-19.</p>
 */
public enum TextOrigin {
	HUMAN,
	SUGGESTED
}
