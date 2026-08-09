package org.slcp.service.invitations;

import jakarta.annotation.PostConstruct;
import org.slcp.service.domain.ProjectRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio del mensaje de invitacion.
 *
 * <p>El envio no es condicion para invitar. Si falla, la invitacion sigue
 * emitida y valida, y quien invita recibe el enlace para hacerlo llegar por otro
 * medio. Lo contrario haria que una caida del servidor de correo impidiese
 * incorporar a nadie, lo que seria peor que una entrega manual.</p>
 */
@Component
public class InvitationMailer {

	/** Resultado del intento de entrega. */
	public record Entrega(boolean enviado, String detalle) {
	}

	private static final Logger log = LoggerFactory.getLogger(InvitationMailer.class);

	private final JavaMailSender sender;
	private final MailProperties propiedades;
	private final String host;
	private final int puerto;

	public InvitationMailer(JavaMailSender sender, MailProperties propiedades,
			@Value("${spring.mail.host:}") String host,
			@Value("${spring.mail.port:0}") int puerto) {
		this.sender = sender;
		this.propiedades = propiedades;
		this.host = host;
		this.puerto = puerto;
	}

	/**
	 * Deja constancia al arrancar de si el correo va a salir o no.
	 *
	 * <p>Sin esta linea, un despliegue sin credenciales se comporta igual que uno
	 * con credenciales equivocadas hasta que alguien invita, y para entonces la
	 * causa ya no esta a la vista. Nunca se registra la contrasena.</p>
	 */
	@PostConstruct
	void anunciarEstado() {
		if (estaActivo()) {
			log.info("Correo saliente ACTIVO: {}:{} como {} — los enlaces se enviaran por correo",
					host, puerto, propiedades.from());
		} else {
			log.warn("Correo saliente DESACTIVADO: no hay remitente configurado. "
					+ "Los enlaces de invitacion se devolveran a quien invita. "
					+ "Para activarlo, defina SLCP_MAIL_USERNAME y SLCP_MAIL_PASSWORD "
					+ "y reinicie el servicio.");
		}
	}

	public boolean estaActivo() {
		return propiedades.activo();
	}

	/** Direccion completa del enlace, tal como aparecera en el mensaje. */
	public String enlaceCompleto(String rutaRelativa) {
		return InvitationMessage.enlaceCompleto(propiedades.baseUrl(), rutaRelativa);
	}

	/**
	 * Intenta entregar la invitacion.
	 *
	 * @return el resultado, que nunca lanza excepcion: quien invita debe poder
	 *         saber que la entrega fallo sin que ello deshaga la invitacion
	 */
	public Entrega enviar(String destinatario, String nombreProyecto, String quienInvita,
			ProjectRole rol, String rutaRelativa, int diasDePlazo, boolean tieneCuenta) {

		if (!estaActivo()) {
			return new Entrega(false,
					"El envio de correo no esta configurado en este despliegue");
		}

		try {
			SimpleMailMessage mensaje = new SimpleMailMessage();
			mensaje.setFrom(propiedades.from());
			mensaje.setTo(destinatario);
			mensaje.setSubject(InvitationMessage.asunto(nombreProyecto));
			mensaje.setText(InvitationMessage.cuerpo(nombreProyecto, quienInvita, rol,
					enlaceCompleto(rutaRelativa), diasDePlazo, tieneCuenta));

			sender.send(mensaje);
			log.info("Invitacion entregada al servidor de correo para {}", destinatario);
			return new Entrega(true, "");

		} catch (MailException e) {
			// El motivo se devuelve tal cual porque quien invita necesita saber si el
			// problema es de credenciales, de red o de la direccion de destino. Y se
			// registra con la traza completa, que es donde esta el detalle util.
			String motivo = e.getMostSpecificCause().getMessage();
			log.error("No se pudo entregar la invitacion a {}: {}", destinatario, motivo, e);
			return new Entrega(false, motivo);
		}
	}
}
