package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta de usuario de la plataforma.
 *
 * <p>Realiza TRC-03: el identificador interno es un UUID que nunca se reutiliza
 * y que sirve de extremo a los enlaces, mientras que el identificador legible es
 * solo presentacion. Realiza TRC-04 mediante control de version optimista.</p>
 *
 * <p>La contrasena no se almacena: se guarda su verificador, conforme a FUN-04.
 * En este incremento el campo queda vacio, porque la autenticacion se incorpora
 * en el siguiente.</p>
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "username", nullable = false, length = 60)
	private String username;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "full_name", nullable = false, length = 160)
	private String fullName;

	@Column(name = "password_verifier")
	private String passwordVerifier;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "platform_role", nullable = false, length = 20)
	private PlatformRole platformRole;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	@Column(name = "version", nullable = false)
	private int version;

	/** Constructor exigido por JPA. */
	protected User() {
	}

	private User(UUID id, String readableId, String username, String email,
			String fullName, UserStatus status, Instant createdAt) {
		this.id = id;
		this.readableId = readableId;
		this.username = username;
		this.email = email;
		this.fullName = fullName;
		this.status = status;
		this.createdAt = createdAt;
		this.platformRole = PlatformRole.MEMBER;
		this.mustChangePassword = false;
	}

	/**
	 * Crea una solicitud de registro. La cuenta nace pendiente de aprobacion y
	 * sin capacidad alguna, conforme a FUN-15.
	 *
	 * @param passwordVerifier verificador ya derivado. La contrasena en claro no
	 *                         entra nunca en el dominio, conforme a FUN-04
	 */
	public static User solicitar(String username, String email, String fullName,
			String passwordVerifier, long secuencia, Instant momento) {
		UUID id = UUID.randomUUID();
		String readableId = String.format("USR-ACC-%04d-v1", secuencia);
		User usuario = new User(id, readableId, username.trim(),
				email.trim().toLowerCase(), fullName.trim(), UserStatus.PENDING_APPROVAL, momento);
		usuario.passwordVerifier = passwordVerifier;
		return usuario;
	}

	/**
	 * Cambia el estado comprobando que la transicion sea admisible.
	 *
	 * @throws IllegalStateException si la transicion no esta permitida
	 */
	public void transitarA(UserStatus destino) {
		if (!status.puedeTransitarA(destino)) {
			throw new IllegalStateException(
					"Transicion no admitida de " + status + " a " + destino);
		}
		this.status = destino;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	public UserStatus getStatus() {
		return status;
	}

	/**
	 * Verificador de la contrasena. Nunca la contrasena: FUN-04 prohíbe
	 * almacenarla, y este metodo devuelve el valor derivado.
	 */
	public String getPasswordVerifier() {
		return passwordVerifier;
	}

	/** Indica si la cuenta puede iniciar sesion en este momento. */
	public boolean puedeIniciarSesion() {
		return status == UserStatus.ACTIVE && passwordVerifier != null;
	}

	public PlatformRole getPlatformRole() {
		return platformRole;
	}

	public boolean esAdministrador() {
		return platformRole == PlatformRole.ADMINISTRATOR;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	/**
	 * Solicitud de quien se autorregistra, que aspira a facilitador.
	 *
	 * <p>FUN-15 establece que el autorregistro concede el rol de facilitador de
	 * proyectos, que es quien puede crearlos. La atribucion se fija ya, y no
	 * surte efecto hasta que la cuenta pasa a operativa.</p>
	 */
	public static User solicitarComoFacilitador(String username, String email, String fullName,
			String passwordVerifier, long secuencia, Instant momento) {
		User usuario = solicitar(username, email, fullName, passwordVerifier, secuencia, momento);
		usuario.platformRole = PlatformRole.FACILITATOR;
		return usuario;
	}

	/**
	 * Aprueba la solicitud de registro.
	 *
	 * <p>Realiza FUN-16. La comprobacion de la transicion recae en la maquina de
	 * estados, de modo que aprobar una cuenta ya aprobada o una rechazada falla
	 * en lugar de pasar inadvertido.</p>
	 */
	public void aprobar() {
		transitarA(UserStatus.ACTIVE);
	}

	/** Rechaza la solicitud de registro. El rechazo es terminal. */
	public void rechazar() {
		transitarA(UserStatus.REJECTED);
	}

	/** Fija un verificador nuevo y levanta la obligacion de cambiar la contrasena. */
	public void cambiarVerificador(String nuevoVerificador) {
		this.passwordVerifier = nuevoVerificador;
		this.mustChangePassword = false;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		return otro instanceof User user && Objects.equals(id, user.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
