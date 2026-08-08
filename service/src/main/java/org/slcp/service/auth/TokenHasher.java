package org.slcp.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generacion y resumen de tokens de renovacion.
 *
 * <p>El token se genera con un generador criptografico y se almacena solo su
 * resumen, conforme a SEC-03. Se emplea SHA-256 y no una funcion de derivacion
 * lenta a proposito: el token ya tiene entropia suficiente, de modo que no hay
 * nada que un ataque de diccionario pueda aprovechar, y una funcion lenta solo
 * penalizaria cada renovacion legitima.</p>
 */
public final class TokenHasher {

	private static final SecureRandom ALEATORIO = new SecureRandom();
	private static final int BYTES = 32;

	private TokenHasher() {
	}

	/** Genera un token nuevo, en codificacion apta para transporte. */
	public static String generar() {
		byte[] datos = new byte[BYTES];
		ALEATORIO.nextBytes(datos);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
	}

	/** Resumen hexadecimal del token, que es lo unico que se almacena. */
	public static String resumir(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 debe estar disponible en toda JVM conforme", e);
		}
	}
}
