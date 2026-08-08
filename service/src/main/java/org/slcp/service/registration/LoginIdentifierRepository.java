package org.slcp.service.registration;

import java.util.Optional;
import org.slcp.service.domain.LoginIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Resolucion de un identificador de acceso a su cuenta.
 *
 * <p>Es la consulta que sostiene el inicio de sesion con nombre de usuario o
 * con correo indistintamente, conforme a FUN-03.</p>
 */
public interface LoginIdentifierRepository extends JpaRepository<LoginIdentifier, String> {

	default Optional<LoginIdentifier> resolver(String valorAportado) {
		return findById(valorAportado.trim().toLowerCase());
	}
}
