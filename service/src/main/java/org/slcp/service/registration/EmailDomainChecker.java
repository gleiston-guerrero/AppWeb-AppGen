package org.slcp.service.registration;

import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * Comprueba que el dominio de un correo puede recibir mensajes.
 *
 * <p>Consulta los registros de intercambio de correo del dominio. Si no tiene
 * ninguno, ninguna direccion de ese dominio puede recibir nada, y aceptarla
 * seria admitir una cuenta con la que nunca podra contactarse.</p>
 *
 * <p><strong>Lo que esta comprobacion no hace.</strong> Dice que el dominio
 * puede recibir correo, no que el buzon exista. Preguntarselo al servidor de
 * destino es tecnicamente posible y practicamente inutil: casi todos responden
 * afirmativamente a cualquier direccion para no revelar cuales tienen, y quienes
 * lo hacen de otro modo suelen bloquear a quien pregunta.</p>
 *
 * <p>La unica comprobacion concluyente es enviar un enlace y esperar a que
 * alguien lo abra, porque acredita a la vez que la direccion existe y que
 * pertenece a quien dice. Eso es exactamente el flujo de invitacion de
 * SLCP-ADR-0005, y esta clase es solo un filtro previo que evita lo evidente.</p>
 *
 * <p>Ante un fallo de resolucion la comprobacion no bloquea. Una caida del
 * servicio de nombres impediria registrarse a todo el mundo, lo que seria peor
 * que admitir un dominio dudoso que el enlace de confirmacion descartara
 * despues.</p>
 */
public class EmailDomainChecker {

	/** Resultado de la comprobacion. */
	public enum Resultado {
		/** El dominio declara servidores de correo. */
		ACEPTA_CORREO,

		/** El dominio existe pero no recibe correo. */
		SIN_SERVIDOR_DE_CORREO,

		/** El dominio no existe. */
		DOMINIO_INEXISTENTE,

		/** No se pudo comprobar. No bloquea. */
		NO_COMPROBADO
	}

	private final int tiempoLimiteMs;
	private final Map<String, Resultado> cache = new ConcurrentHashMap<>();

	public EmailDomainChecker(int tiempoLimiteMs) {
		this.tiempoLimiteMs = tiempoLimiteMs;
	}

	/**
	 * Comprueba el dominio de la direccion indicada.
	 *
	 * <p>El resultado se recuerda: los dominios de una institucion se repiten en
	 * casi todos los registros, y volver a preguntar por cada uno anadiria
	 * latencia sin aportar nada.</p>
	 */
	public Resultado comprobar(String email) {
		String dominio = dominioDe(email);
		if (dominio == null) {
			return Resultado.NO_COMPROBADO;
		}
		return cache.computeIfAbsent(dominio, this::consultar);
	}

	/** Indica si la direccion debe rechazarse por su dominio. */
	public boolean debeRechazarse(String email) {
		Resultado r = comprobar(email);
		return r == Resultado.SIN_SERVIDOR_DE_CORREO || r == Resultado.DOMINIO_INEXISTENTE;
	}

	/** Explicacion destinada a quien se registra. */
	public String explicacion(Resultado resultado) {
		return switch (resultado) {
			case DOMINIO_INEXISTENTE ->
					"El dominio de ese correo no existe. Compruebe si lo escribio bien";
			case SIN_SERVIDOR_DE_CORREO ->
					"Ese dominio no puede recibir correo, de modo que no podriamos contactarle";
			case ACEPTA_CORREO, NO_COMPROBADO -> "";
		};
	}

	private Resultado consultar(String dominio) {
		try {
			Hashtable<String, String> entorno = new Hashtable<>();
			entorno.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			entorno.put("com.sun.jndi.dns.timeout.initial", String.valueOf(tiempoLimiteMs));
			entorno.put("com.sun.jndi.dns.timeout.retries", "1");

			DirContext contexto = new InitialDirContext(entorno);
			Attributes atributos = contexto.getAttributes(dominio, new String[] { "MX" });
			Attribute mx = atributos.get("MX");

			return (mx != null && mx.size() > 0)
					? Resultado.ACEPTA_CORREO
					: Resultado.SIN_SERVIDOR_DE_CORREO;

		} catch (NameNotFoundException e) {
			return Resultado.DOMINIO_INEXISTENTE;
		} catch (Exception e) {
			// Sin resolucion no se bloquea: una caida del servicio de nombres no debe
			// impedir que nadie se registre.
			return Resultado.NO_COMPROBADO;
		}
	}

	private String dominioDe(String email) {
		if (email == null) {
			return null;
		}
		int arroba = email.lastIndexOf('@');
		if (arroba < 0 || arroba == email.length() - 1) {
			return null;
		}
		String dominio = email.substring(arroba + 1).trim().toLowerCase(Locale.ROOT);
		return dominio.isEmpty() ? null : dominio;
	}
}
