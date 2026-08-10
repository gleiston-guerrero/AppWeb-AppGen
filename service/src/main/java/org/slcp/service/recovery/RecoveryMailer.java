package org.slcp.service.recovery;

import org.slcp.service.invitations.InvitationMessage;
import org.slcp.service.invitations.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio del correo de recuperacion.
 *
 * <p>A diferencia de las invitaciones, aqui el envio si es condicion: sin correo
 * no hay forma de entregar el enlace sin comprometer la cuenta.</p>
 */
@Component
public class RecoveryMailer {

	/** Resultado del intento de entrega. */
	public record Entrega(boolean enviado, String detalle) {
	}

	private static final Logger log = LoggerFactory.getLogger(RecoveryMailer.class);

	private final JavaMailSender sender;
	private final MailProperties propiedades;

	public RecoveryMailer(JavaMailSender sender, MailProperties propiedades) {
		this.sender = sender;
		this.propiedades = propiedades;
	}

	public Entrega enviar(String destinatario, String nombre, String token, int minutos) {
		if (!propiedades.activo()) {
			return new Entrega(false, "el envio de correo no esta configurado en este despliegue");
		}

		String enlace = InvitationMessage.enlaceCompleto(propiedades.baseUrl(),
				"/recuperar/" + token);

		try {
			SimpleMailMessage mensaje = new SimpleMailMessage();
			mensaje.setFrom(propiedades.from());
			mensaje.setTo(destinatario);
			mensaje.setSubject(RecoveryMessage.asunto());
			mensaje.setText(RecoveryMessage.cuerpo(nombre, enlace, minutos));

			sender.send(mensaje);
			log.info("Correo de recuperacion entregado al servidor para {}", destinatario);
			return new Entrega(true, "");

		} catch (MailException e) {
			String motivo = e.getMostSpecificCause().getMessage();
			log.error("No se pudo entregar la recuperacion a {}: {}", destinatario, motivo, e);
			return new Entrega(false, motivo);
		}
	}
}
