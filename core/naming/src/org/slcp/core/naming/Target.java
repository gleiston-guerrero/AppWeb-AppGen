package org.slcp.core.naming;

/**
 * Destinos de generacion contemplados por SLCP-DOC-001 (requisitos TGT-01 y
 * TGT-02), mas los destinos estructurales de la propia plataforma.
 *
 * <p>El limite de longitud es el del identificador sin entrecomillar de cada
 * destino. El valor 0 significa que el destino no impone limite practico y que
 * la regla de NAM-06 no se aplica salvo que el proyecto configure uno.</p>
 */
public enum Target {

	JAVA(0),
	CSHARP(0),
	PHP(0),
	SQL_POSTGRES(63),
	SQL_MYSQL(64),
	SQL_MARIADB(64),
	SQL_ORACLE(128),
	REST(0),
	JSON(0),
	FILE(0);

	private final int defaultMaxLength;

	Target(int defaultMaxLength) {
		this.defaultMaxLength = defaultMaxLength;
	}

	public int getDefaultMaxLength() {
		return defaultMaxLength;
	}
}
