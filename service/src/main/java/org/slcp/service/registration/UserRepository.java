package org.slcp.service.registration;

import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las cuentas de usuario. */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsernameIgnoreCase(String username);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmailIgnoreCase(String email);
}
