package org.slcp.service.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Oraculo del cifrado de credenciales.
 *
 * <p>Estas pruebas vigilan lo que hace que guardar una clave de API sea
 * admisible. Si alguna deja de pasar, la plataforma esta guardando credenciales
 * de forma que no deberia.</p>
 */
class CredentialCipherTest {

	private static final String MAESTRA = "clave-maestra-de-la-instalacion-para-pruebas";
	private static final String SECRETO = "sk-ant-api03-EJEMPLO-no-es-real-9f4c2b7a";

	private final CredentialCipher cifrador = new CredentialCipher(MAESTRA);

	@Test
	@DisplayName("El secreto no aparece en el texto cifrado")
	void noApareceEnClaro() {
		String cifrado = cifrador.cifrar(SECRETO);

		assertThat(cifrado).doesNotContain(SECRETO);
		assertThat(cifrado).doesNotContain("9f4c2b7a");
	}

	@Test
	@DisplayName("Lo cifrado se descifra a lo mismo")
	void ciclo() {
		assertThat(cifrador.descifrar(cifrador.cifrar(SECRETO))).isEqualTo(SECRETO);
	}

	@Test
	@DisplayName("Dos cifrados del mismo secreto son distintos")
	void cifradosDistintos() {
		// Si fueran iguales, quien viera la base sabria que dos proyectos usan la
		// misma credencial sin descifrar ninguna.
		assertThat(cifrador.cifrar(SECRETO)).isNotEqualTo(cifrador.cifrar(SECRETO));
	}

	@Test
	@DisplayName("Un texto cifrado alterado se rechaza, no se descifra a otra cosa")
	void alteradoSeRechaza() {
		// Sin autenticacion, quien pudiera escribir en la base sustituiria la clave
		// por una suya y la plataforma la usaria sin notarlo.
		String cifrado = cifrador.cifrar(SECRETO);
		char[] roto = cifrado.toCharArray();
		roto[20] = roto[20] == 'A' ? 'B' : 'A';

		assertThatThrownBy(() -> cifrador.descifrar(new String(roto)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("vuelva a introducir");
	}

	@Test
	@DisplayName("Con otra clave maestra no se descifra")
	void otraMaestraNoDescifra() {
		String cifrado = cifrador.cifrar(SECRETO);

		assertThatThrownBy(() -> new CredentialCipher("otra-clave-distinta").descifrar(cifrado))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("Sin clave maestra no se construye")
	void sinMaestraNoArranca() {
		assertThatThrownBy(() -> new CredentialCipher(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("master-key");
	}

	@Test
	@DisplayName("La pista no permite reconstruir la credencial")
	void pistaInsuficiente() {
		String pista = CredentialCipher.pista(SECRETO);

		assertThat(pista).isEqualTo("…2b7a");
		assertThat(pista.length()).isLessThan(8);
	}

	@Test
	@DisplayName("Una credencial demasiado corta no revela nada en su pista")
	void pistaDeCortaNoRevela() {
		assertThat(CredentialCipher.pista("abc")).isEqualTo("…");
	}
}
