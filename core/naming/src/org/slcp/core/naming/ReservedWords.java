package org.slcp.core.naming;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Listas de exclusion de palabras reservadas por destino (requisito NAM-05).
 *
 * <p>ADVERTENCIA DE ALCANCE: las listas de esta clase son una semilla verificada
 * pero deliberadamente incompleta. El Anexo A de SLCP-DOC-001 exige construir la
 * lista definitiva a partir de las fuentes oficiales de cada destino (serie
 * ISO/IEC 9075 para SQL, documentacion de cada gestor para sus dialectos, y las
 * especificaciones de lenguaje de Java, C# y PHP), y versionarla junto al
 * descriptor del destino. La carga de esa lista completa es responsabilidad del
 * descriptor, no de esta clase: aqui solo reside el minimo necesario para que la
 * comprobacion funcione y quede probada.</p>
 */
public final class ReservedWords {

	private ReservedWords() {
	}

	/** Palabras clave de Java (JLS, seccion 3.9), incluidas las reservadas no usadas. */
	private static final Set<String> JAVA = unmodifiable(
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
			"class", "const", "continue", "default", "do", "double", "else", "enum",
			"extends", "final", "finally", "float", "for", "goto", "if", "implements",
			"import", "instanceof", "int", "interface", "long", "native", "new",
			"package", "private", "protected", "public", "return", "short", "static",
			"strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
			"transient", "try", "void", "volatile", "while",
			"true", "false", "null");

	/** Palabras clave reservadas de C#. */
	private static final Set<String> CSHARP = unmodifiable(
			"abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char",
			"checked", "class", "const", "continue", "decimal", "default", "delegate",
			"do", "double", "else", "enum", "event", "explicit", "extern", "false",
			"finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit",
			"in", "int", "interface", "internal", "is", "lock", "long", "namespace",
			"new", "null", "object", "operator", "out", "override", "params",
			"private", "protected", "public", "readonly", "ref", "return", "sbyte",
			"sealed", "short", "sizeof", "stackalloc", "static", "string", "struct",
			"switch", "this", "throw", "true", "try", "typeof", "uint", "ulong",
			"unchecked", "unsafe", "ushort", "using", "virtual", "void", "volatile",
			"while");

	/** Palabras reservadas de PHP. */
	private static final Set<String> PHP = unmodifiable(
			"abstract", "and", "array", "as", "break", "callable", "case", "catch",
			"class", "clone", "const", "continue", "declare", "default", "do", "echo",
			"else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
			"endswitch", "endwhile", "enum", "extends", "final", "finally", "fn",
			"for", "foreach", "function", "global", "goto", "if", "implements",
			"include", "instanceof", "insteadof", "interface", "isset", "list",
			"match", "namespace", "new", "or", "print", "private", "protected",
			"public", "readonly", "require", "return", "static", "switch", "throw",
			"trait", "try", "unset", "use", "var", "while", "xor", "yield");

	/** Nucleo de palabras reservadas comunes a los dialectos SQL considerados. */
	private static final Set<String> SQL_CORE = unmodifiable(
			"all", "alter", "and", "any", "as", "asc", "between", "both", "by",
			"case", "cast", "check", "column", "constraint", "create", "cross",
			"current_date", "current_time", "current_timestamp", "current_user",
			"default", "delete", "desc", "distinct", "drop", "else", "end", "except",
			"exists", "false", "for", "foreign", "from", "full", "grant", "group",
			"having", "in", "index", "inner", "insert", "intersect", "into", "is",
			"join", "leading", "left", "like", "limit", "natural", "not", "null",
			"on", "only", "or", "order", "outer", "primary", "references", "revoke",
			"right", "select", "session_user", "set", "some", "table", "then", "to",
			"trailing", "true", "union", "unique", "update", "user", "using",
			"values", "when", "where", "with");

	/** Reservadas adicionales frecuentes en Oracle Database. */
	private static final Set<String> ORACLE_EXTRA = unmodifiable(
			"access", "audit", "cluster", "comment", "compress", "date", "file",
			"immediate", "increment", "initial", "level", "long", "maxextents",
			"minus", "mode", "noaudit", "nocompress", "number", "offline", "online",
			"pctfree", "raw", "resource", "rowid", "rownum", "share", "size",
			"start", "successful", "synonym", "sysdate", "uid", "validate",
			"varchar2");

	/** Reservadas adicionales frecuentes en MySQL y MariaDB. */
	private static final Set<String> MYSQL_EXTRA = unmodifiable(
			"accessible", "analyze", "before", "change", "condition", "database",
			"databases", "delayed", "describe", "div", "dual", "each", "enclosed",
			"escaped", "explain", "float", "force", "fulltext", "high_priority",
			"ignore", "infile", "int", "keys", "kill", "lines", "load", "lock",
			"low_priority", "match", "mediumint", "middleint", "optimize",
			"outfile", "purge", "read", "regexp", "rename", "replace", "require",
			"rlike", "schema", "schemas", "separator", "spatial", "sql_big_result",
			"starting", "straight_join", "terminated", "tinyint", "unlock",
			"unsigned", "usage", "varcharacter", "while", "write", "zerofill");

	/**
	 * Indica si el identificador ya renderizado colisiona con una palabra
	 * reservada del destino.
	 *
	 * <p>La sensibilidad a mayusculas depende del destino y no es uniforme, tal
	 * como fijo la enmienda A-001 del oraculo: Java y C# tratan sus palabras
	 * clave de forma sensible a mayusculas, por lo que un tipo llamado
	 * {@code Static} o una propiedad llamada {@code Namespace} son legales; PHP
	 * las trata de forma insensible; y los dialectos SQL considerados pliegan
	 * los identificadores sin entrecomillar, de modo que tambien exigen
	 * comparacion insensible.</p>
	 */
	public static boolean isReserved(Target target, String rendered) {
		String lower = rendered.toLowerCase();
		switch (target) {
			case JAVA:
				return JAVA.contains(rendered);
			case CSHARP:
				return CSHARP.contains(rendered);
			case PHP:
				return PHP.contains(lower);
			case SQL_POSTGRES:
				return SQL_CORE.contains(lower);
			case SQL_ORACLE:
				return SQL_CORE.contains(lower) || ORACLE_EXTRA.contains(lower);
			case SQL_MYSQL:
			case SQL_MARIADB:
				return SQL_CORE.contains(lower) || MYSQL_EXTRA.contains(lower);
			case REST:
			case JSON:
			case FILE:
			default:
				return false;
		}
	}

	private static Set<String> unmodifiable(String... words) {
		return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(words)));
	}
}
