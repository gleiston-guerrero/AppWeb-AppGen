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
