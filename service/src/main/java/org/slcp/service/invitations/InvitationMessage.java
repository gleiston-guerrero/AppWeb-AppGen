package org.slcp.service.invitations;

import org.slcp.service.domain.ProjectRole;

/**
 * Composicion del mensaje de invitacion.
 *
 * <p>Se separa del envio a proposito: componer un texto es una operacion pura y
 * puede comprobarse sin servidor de correo, mientras que enviarlo no. Sin esta
 * separacion, ninguna prueba podria verificar lo que la persona va a leer.</p>
 *
 * <p>El mensaje se atiene a INV-03: nombra el proyecto, quien invita y el rol, y
 * nada mas. A una direccion todavia no verificada no se le revelan requisitos,
 * artefactos ni quienes forman el equipo.</p>
 */
public final class InvitationMessage {

	private InvitationMessage() {
	}

	/** Asunto del mensaje. */
	public static String asunto(String nombreProyecto) {
		return "Invitacion al proyecto " + nombreProyecto;
	}

	/**
	 * Cuerpo del mensaje, en texto plano.
	 *
	 * <p>Se elige texto plano y no formato enriquecido: llega intacto a cualquier
	 * lector, no depende de que se carguen recursos externos y no ofrece a nadie
	 * la ocasion de disfrazar el enlace tras un texto distinto, que es
	 * precisamente el mecanismo del enganno por correo.</p>
	 */
	public static String cuerpo(String nombreProyecto, String quienInvita,
			ProjectRole rol, String enlace, int diasDePlazo, boolean tieneCuenta) {

		StringBuilder texto = new StringBuilder();

		texto.append("Hola,\n\n");
		texto.append(quienInvita).append(" le invita a participar en el proyecto \"")
				.append(nombreProyecto).append("\" en la plataforma SLCP.\n\n");

		texto.append("Su rol sera: ").append(rol.getEtiqueta()).append("\n");
		texto.append(alcance(rol)).append("\n\n");

		if (tieneCuenta) {
			texto.append("Su direccion ya tiene cuenta en la plataforma. Inicie sesion y ")
					.append("encontrara la invitacion en su espacio de trabajo, donde podra ")
					.append("aceptarla o rechazarla.\n\n");
		} else {
			texto.append("Para incorporarse necesita completar su registro, eligiendo un nombre ")
					.append("de usuario y una clave de acceso propia.\n\n");
		}

		// El enlace va en su propia linea y precedido de una etiqueta inequivoca. Sin
		// ella, una frase terminada en dos puntos justo antes del enlace se lee como
		// si el enlace fuese el dato que la frase anuncia.
		texto.append("Enlace de la invitacion:\n");
		texto.append("    ").append(enlace).append("\n\n");

		texto.append("El enlace caduca en ").append(diasDePlazo)
				.append(" dias y solo puede usarse una vez.\n\n");

		texto.append("Si no esperaba esta invitacion, no haga nada: sin abrir el enlace no se ")
				.append("crea ninguna cuenta ni se le incorpora a nada.\n\n");

		texto.append("---\n");
		texto.append("Plataforma SLCP\n");
		texto.append("Universidad Tecnica Estatal de Quevedo\n");
		texto.append("Este mensaje se genero automaticamente. No responda a esta direccion.\n");

		return texto.toString();
	}

	/** Enlace completo a partir de la direccion publica y la ruta relativa. */
	public static String enlaceCompleto(String baseUrl, String rutaRelativa) {
		String base = baseUrl == null ? "" : baseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String ruta = rutaRelativa == null ? "" : rutaRelativa.trim();
		if (!ruta.startsWith("/")) {
			ruta = "/" + ruta;
		}
		return base + ruta;
	}

	private static String alcance(ProjectRole rol) {
		return switch (rol) {
			case PROJECT_FACILITATOR -> "Organiza el proyecto, planifica e incorpora al equipo.";
			case TEAM_MEMBER -> "Trabaja los requisitos, genera y modifica artefactos.";
			case PRODUCT_OWNER -> "Verifica y aprueba. No modifica nada.";
		};
	}
}
