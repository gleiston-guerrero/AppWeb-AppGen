package org.slcp.service.recovery;

/**
 * Composicion del correo de recuperacion.
 *
 * <p>Se separa del envio para poder comprobar lo que la persona va a leer sin
 * servidor de correo, igual que en las invitaciones.</p>
 */
public final class RecoveryMessage {

	private RecoveryMessage() {
	}

	public static String asunto() {
		return "Recuperacion de acceso a la plataforma SLCP";
	}

	/**
	 * Cuerpo del mensaje.
	 *
	 * <p>Incluye dos avisos que no son adorno. El primero explica que hacer si no
	 * pidio nada, porque un correo de recuperacion no solicitado suele ser la
	 * primera senal de que alguien intenta entrar en la cuenta. El segundo
	 * advierte de que el cambio cerrara las sesiones abiertas, para que no
	 * sorprenda.</p>
	 */
	public static String cuerpo(String nombre, String enlace, int minutos) {
		return """
				Hola %s,

				Alguien ha solicitado cambiar la contrasena de su cuenta en la plataforma SLCP.

				Enlace para cambiarla:

				    %s

				El enlace caduca en %d minutos y solo puede usarse una vez.

				Si no ha sido usted, no haga nada: sin abrir el enlace su contrasena no cambia.
				Conviene, eso si, que revise si alguien mas conoce sus credenciales.

				Al cambiar la contrasena se cerraran todas las sesiones abiertas de su cuenta,
				incluidas las suyas en otros dispositivos.

				---
				Plataforma SLCP
				Universidad Tecnica Estatal de Quevedo
				Este mensaje se genero automaticamente. No responda a esta direccion.
				""".formatted(nombre, enlace, minutos);
	}
}
