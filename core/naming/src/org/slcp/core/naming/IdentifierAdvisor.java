package org.slcp.core.naming;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validacion de un identificador propuesto por una persona y propuesta de
 * alternativas legales cuando el destino no lo admite.
 *
 * <p>Realiza la regla acordada con la parte interesada: si el lenguaje o el
 * gestor de base de datos admite el nombre propuesto, la plataforma lo acepta
 * sin mas; si no lo admite, propone alternativas legales y la persona elige una
 * de ellas. En ningun caso la plataforma sustituye el nombre por su cuenta ni lo
 * entrecomilla en silencio, conforme a NAM-05.</p>
 *
 * <p>La clase es deliberadamente determinista y sin dependencias externas. La
 * legalidad de un identificador es una cuestion decidible mediante consulta a
 * una lista publicada y aplicacion de reglas de forma, de modo que no procede
 * resolverla con un motor no determinista: hacerlo introducira variabilidad
 * donde hoy hay certeza y obligaria a declarar el regimen correspondiente
 * conforme a TRC-23 sin ganancia alguna.</p>
 */
public final class IdentifierAdvisor {

	private IdentifierAdvisor() {
	}

	/** Resultado de validar un identificador propuesto. */
	public enum Status {
		/** El destino admite el identificador tal como se propuso. */
		LEGAL,
		/** Colisiona con una palabra reservada del destino. */
		RESERVED_WORD,
		/** Excede el limite de longitud del destino. */
		TOO_LONG,
		/** No puede producir un identificador valido. */
		INVALID_TERM,
		/** El termino esta vacio. */
		EMPTY_TERM
	}

	/** Resultado de la validacion: estado y, si es legal, la forma resultante. */
	public static final class Validation {
		private final Status status;
		private final String rendered;

		Validation(Status status, String rendered) {
			this.status = status;
			this.rendered = rendered;
		}

		public Status getStatus() {
			return status;
		}

		public String getRendered() {
			return rendered;
		}

		public boolean isLegal() {
			return status == Status.LEGAL;
		}
	}

	/**
	 * Comprueba si el destino admite el termino propuesto para la clase de
	 * identificador indicada.
	 *
	 * @param singular forma singular propuesta
	 * @param plural   forma plural propuesta, obligatoria para tablas y rutas
	 * @param target   destino de generacion
	 * @param kind     clase de identificador
	 * @return la validacion, con la forma resultante cuando es legal
	 */
	public static Validation validate(String singular, String plural, Target target, Kind kind) {
		return validate(singular, plural, target, kind, target.getDefaultMaxLength());
	}

	/** Variante que permite imponer un limite de longitud distinto. */
	public static Validation validate(String singular, String plural, Target target, Kind kind, int maxLength) {
		try {
			String rendered = NamingTransform.render(singular, plural, target, kind, 0);
			if (maxLength > 0 && rendered.length() > maxLength) {
				return new Validation(Status.TOO_LONG, rendered);
			}
			return new Validation(Status.LEGAL, rendered);
		} catch (NamingException e) {
			switch (e.getCode()) {
				case RESERVED_WORD:
					return new Validation(Status.RESERVED_WORD, null);
				case INVALID_TERM:
					return new Validation(Status.INVALID_TERM, null);
				case MISSING_PLURAL:
					return new Validation(Status.INVALID_TERM, null);
				case EMPTY_TERM:
				default:
					return new Validation(Status.EMPTY_TERM, null);
			}
		}
	}

	/**
	 * Propone alternativas legales cuando el termino no es admisible.
	 *
	 * <p>Las estrategias son mecanicas y se aplican en orden fijo, de modo que la
	 * propuesta es reproducible: primero calificar con el termino del contexto
	 * que contiene al identificador y despues calificar con el papel que cumple
	 * el atributo cuando se conoce. Solo se devuelven candidatos que resultan
	 * legales en el destino.</p>
	 *
	 * <p>Se descarto de forma expresa la estrategia de emplear la forma plural
	 * como alternativa. Es legal pero produce nombres falsos: una columna que
	 * guarda una sola orden no debe llamarse ordenes. La legalidad no basta para
	 * proponer un nombre.</p>
	 *
	 * <p>Ninguna de estas estrategias determina si el nombre resultante es
	 * <em>bueno</em>, que es una cuestion de significado y no de legalidad. Esa
	 * valoracion corresponde a la persona, con la asistencia opcional descrita en
	 * SLCP-DOC-013.</p>
	 *
	 * @param context  termino de la entidad o modulo que contiene al identificador;
	 *                 puede ser nulo
	 * @param roleHint papel del atributo, por ejemplo codigo, valor o numero;
	 *                 puede ser nulo
	 * @return lista ordenada de candidatos legales, vacia si el termino ya es
	 *         legal o si no hay informacion para construir ninguno
	 */
	public static List<String> suggest(String singular, String plural, Target target, Kind kind,
			String context, String roleHint) {

		List<String> candidates = new ArrayList<>();
		if (validate(singular, plural, target, kind).isLegal()) {
			return candidates;
		}

		Set<String> proposals = new LinkedHashSet<>();
		if (isPresent(context)) {
			proposals.add(context + " " + singular);
		}
		if (isPresent(roleHint)) {
			proposals.add(singular + " " + roleHint);
		}

		for (String proposal : proposals) {
			Validation v = validate(proposal, proposal, target, kind);
			if (v.isLegal() && !candidates.contains(v.getRendered())) {
				candidates.add(v.getRendered());
			}
		}
		return candidates;
	}

	private static boolean isPresent(String s) {
		return s != null && !s.trim().isEmpty();
	}
}
