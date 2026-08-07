package org.slcp.core.naming;

/**
 * Error de transformacion de nomenclatura.
 *
 * <p>Requisito NAM-05: ante una colision con palabra reservada la transformacion
 * aborta con error explicito, nunca entrecomilla el identificador de forma
 * silenciosa. Requisito NAM-07: la pluralizacion procede del glosario y jamas
 * se infiere, de modo que su ausencia tambien es un error.</p>
 */
public class NamingException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Codigo estable del error, verificable por el oraculo de conformidad. */
	public enum Code {
		EMPTY_TERM,
		INVALID_TERM,
		MISSING_PLURAL,
		RESERVED_WORD
	}

	private final Code code;

	public NamingException(Code code, String message) {
		super(code + ": " + message);
		this.code = code;
	}

	public Code getCode() {
		return code;
	}
}
