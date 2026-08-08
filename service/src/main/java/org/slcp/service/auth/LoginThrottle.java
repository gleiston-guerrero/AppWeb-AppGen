package org.slcp.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de intentos de acceso.
 *
 * <p>Es el control que de verdad impide enumerar cuentas y probar contrasenas.
 * Sustituye a la practica de devolver mensajes vagos, que no protege nada que el
 * formulario de registro no revele ya y que en cambio impide a quien se
 * equivoca de buena fe saber que corregir.</p>
 *
 * <p>Cuenta por dos claves a la vez, y ambas importan. Por identificador, para
 * que nadie pruebe muchas contrasenas contra una misma cuenta. Y por origen,
 * para que nadie pruebe una misma contrasena contra muchas cuentas, que es el
 * ataque que un limite por cuenta deja pasar entero.</p>
 *
 * <p>Un acierto borra el contador del identificador, de modo que quien acaba
 * recordando su contrasena no arrastra penalizacion.</p>
 *
 * <p>La implementacion es en memoria y por tanto se pierde al reiniciar y no se
 * comparte entre instancias. Es suficiente mientras el despliegue sea de una
 * sola instancia, tal como fija D-05, y debe sustituirse por almacenamiento
 * compartido antes de desplegar varias.</p>
 */
public class LoginThrottle {

	/** Intentos fallidos consecutivos admitidos antes de bloquear. */
	public static final int MAX_POR_IDENTIFICADOR = 5;

	/** Fallos admitidos desde un mismo origen antes de bloquearlo. */
	public static final int MAX_POR_ORIGEN = 20;

	private final Duration ventana;
	private final Clock clock;
	private final Map<String, Registro> porIdentificador = new ConcurrentHashMap<>();
	private final Map<String, Registro> porOrigen = new ConcurrentHashMap<>();

	public LoginThrottle(Duration ventana, Clock clock) {
		this.ventana = ventana;
		this.clock = clock;
	}

	private static final class Registro {
		private int fallos;
		private Instant primero;

		Registro(Instant momento) {
			this.fallos = 0;
			this.primero = momento;
		}
	}

	/**
	 * Comprueba si el intento puede realizarse.
	 *
	 * @return el motivo del bloqueo, o vacio si puede intentarse
	 */
	public Optional<LoginFailure> comprobar(String identificador, String origen) {
		Instant momento = Instant.now(clock);
		if (bloqueado(porIdentificador, clave(identificador), MAX_POR_IDENTIFICADOR, momento)
				|| bloqueado(porOrigen, origen, MAX_POR_ORIGEN, momento)) {
			return Optional.of(LoginFailure.TOO_MANY_ATTEMPTS);
		}
		return Optional.empty();
	}

	/** Anota un intento fallido en ambos contadores. */
	public void anotarFallo(String identificador, String origen) {
		Instant momento = Instant.now(clock);
		anotar(porIdentificador, clave(identificador), momento);
		anotar(porOrigen, origen, momento);
	}

	/**
	 * Anota un acierto, que borra el contador del identificador.
	 *
	 * <p>El contador del origen no se borra a proposito: quien controla un origen
	 * podria disponer de una cuenta legitima y usarla para limpiar el contador
	 * entre tandas de intentos.</p>
	 */
	public void anotarAcierto(String identificador) {
		porIdentificador.remove(clave(identificador));
	}

	/** Intentos fallidos consecutivos anotados para un identificador. */
	public int fallosDe(String identificador) {
		Registro registro = porIdentificador.get(clave(identificador));
		if (registro == null || caducado(registro, Instant.now(clock))) {
			return 0;
		}
		return registro.fallos;
	}

	private boolean bloqueado(Map<String, Registro> mapa, String clave, int maximo, Instant momento) {
		Registro registro = mapa.get(clave);
		if (registro == null) {
			return false;
		}
		if (caducado(registro, momento)) {
			mapa.remove(clave);
			return false;
		}
		return registro.fallos >= maximo;
	}

	private void anotar(Map<String, Registro> mapa, String clave, Instant momento) {
		mapa.compute(clave, (k, registro) -> {
			if (registro == null || caducado(registro, momento)) {
				registro = new Registro(momento);
			}
			registro.fallos++;
			return registro;
		});
	}

	private boolean caducado(Registro registro, Instant momento) {
		return registro.primero.plus(ventana).isBefore(momento);
	}

	private String clave(String identificador) {
		return identificador == null ? "" : identificador.trim().toLowerCase();
	}
}
