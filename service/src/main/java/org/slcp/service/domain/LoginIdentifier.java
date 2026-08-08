package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

/**
 * Identificador con el que una persona puede iniciar sesion.
 *
 * <p>El nombre de usuario y el correo comparten un unico espacio de nombres, de
 * modo que ninguno de los dos puede coincidir con el del otro extremo en otra
 * cuenta. Sin esta tabla, la unicidad de cada columna por separado admitia una
 * cuenta cuyo nombre de usuario fuese el correo de otra, y FUN-03 exige que la
 * resolucion entre ambos identificadores nunca sea ambigua.</p>
 */
@Entity
@Table(name = "login_identifiers")
public class LoginIdentifier {

	/** Naturaleza del identificador. */
	public enum Kind {
		USERNAME,
		EMAIL
	}

	@Id
	@Column(name = "identifier", nullable = false, length = 254, updatable = false)
	private String identifier;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 10, updatable = false)
	private Kind kind;

	/** Constructor exigido por JPA. */
	protected LoginIdentifier() {
	}

	public static LoginIdentifier de(String valor, UUID userId, Kind kind) {
		LoginIdentifier li = new LoginIdentifier();
		li.identifier = valor.trim().toLowerCase(Locale.ROOT);
		li.userId = userId;
		li.kind = kind;
		return li;
	}

	public String getIdentifier() {
		return identifier;
	}

	public UUID getUserId() {
		return userId;
	}

	public Kind getKind() {
		return kind;
	}
}
