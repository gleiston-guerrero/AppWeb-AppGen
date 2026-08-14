package org.slcp.service.generation;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra las credenciales antes de guardarlas.
 *
 * <p>Una clave de API guardada en claro convierte una copia de la base de datos
 * en un juego de credenciales utilizables: quien la obtenga puede gastar en
 * nombre del proyecto sin dejar rastro en la plataforma. Cifrarla no impide que
 * roben la base, pero hace que lo robado no sirva sin la clave maestra, que vive
 * fuera de ella.</p>
 *
 * <p>Se emplea AES en modo GCM, que ademas de cifrar autentica: un texto cifrado
 * alterado no se descifra a otra cosa, sino que falla. Sin esa autenticacion,
 * quien pudiera escribir en la base podria sustituir la clave por una suya y la
 * plataforma la usaria sin notarlo.</p>
 */
public final class CredentialCipher {

	private static final String ALGORITMO = "AES/GCM/NoPadding";
	private static final int LONGITUD_ETIQUETA = 128;
	private static final int LONGITUD_VECTOR = 12;

	private final SecretKeySpec clave;
	private final SecureRandom azar = new SecureRandom();

	/**
	 * @param maestra clave maestra de la instalacion, en Base64 o como texto
	 * @throws IllegalStateException si no hay clave maestra configurada
	 */
	public CredentialCipher(String maestra) {
		if (maestra == null || maestra.isBlank()) {
			throw new IllegalStateException(
					"Falta la clave maestra de cifrado (slcp.security.master-key). Sin ella no "
							+ "pueden guardarse credenciales, y guardarlas en claro seria peor que "
							+ "no admitirlas");
		}

		byte[] material;
		try {
			material = Base64.getDecoder().decode(maestra);
		} catch (IllegalArgumentException e) {
			material = maestra.getBytes(StandardCharsets.UTF_8);
		}

		// Se deriva una clave de 256 bits del material dado, sea cual sea su
		// longitud: exigir exactamente 32 bytes obligaria a quien instala a
		// generarlos de una forma concreta, y el error se descubriria al arrancar.
		byte[] normalizada = new byte[32];
		for (int i = 0; i < normalizada.length; i++) {
			normalizada[i] = material[i % material.length];
		}
		this.clave = new SecretKeySpec(normalizada, "AES");
	}

	/**
	 * Cifra un secreto.
	 *
	 * <p>El vector de inicializacion viaja delante del texto cifrado: ha de ser
	 * distinto en cada cifrado y no es secreto, de modo que guardarlo junto al
	 * resultado es lo correcto y ahorra una columna que podria desincronizarse.</p>
	 */
	public String cifrar(String secreto) {
		try {
			byte[] vector = new byte[LONGITUD_VECTOR];
			azar.nextBytes(vector);

			Cipher cifrador = Cipher.getInstance(ALGORITMO);
			cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(LONGITUD_ETIQUETA, vector));

			byte[] cifrado = cifrador.doFinal(secreto.getBytes(StandardCharsets.UTF_8));
			byte[] salida = new byte[vector.length + cifrado.length];

			System.arraycopy(vector, 0, salida, 0, vector.length);
			System.arraycopy(cifrado, 0, salida, vector.length, cifrado.length);

			return Base64.getEncoder().encodeToString(salida);

		} catch (Exception e) {
			// No se incluye el secreto en el mensaje: acabaria en los registros.
			throw new IllegalStateException("No se pudo cifrar la credencial", e);
		}
	}

	/** Descifra un secreto. Falla si el texto fue alterado. */
	public String descifrar(String cifrado) {
		try {
			byte[] todo = Base64.getDecoder().decode(cifrado);

			byte[] vector = new byte[LONGITUD_VECTOR];
			System.arraycopy(todo, 0, vector, 0, LONGITUD_VECTOR);

			byte[] cuerpo = new byte[todo.length - LONGITUD_VECTOR];
			System.arraycopy(todo, LONGITUD_VECTOR, cuerpo, 0, cuerpo.length);

			Cipher descifrador = Cipher.getInstance(ALGORITMO);
			descifrador.init(Cipher.DECRYPT_MODE, clave,
					new GCMParameterSpec(LONGITUD_ETIQUETA, vector));

			return new String(descifrador.doFinal(cuerpo), StandardCharsets.UTF_8);

		} catch (Exception e) {
			throw new IllegalStateException(
					"No se pudo descifrar la credencial. O la clave maestra cambio, o el dato fue "
							+ "alterado: en ambos casos vuelva a introducir la credencial", e);
		}
	}

	/**
	 * Pista de una credencial, para que quien la puso reconozca cual es.
	 *
	 * <p>Cuatro caracteres finales. Bastan para distinguir dos claves propias y no
	 * alcanzan para reconstruir ninguna.</p>
	 */
	public static String pista(String secreto) {
		if (secreto == null || secreto.length() < 8) {
			return "…";
		}
		return "…" + secreto.substring(secreto.length() - 4);
	}
}
