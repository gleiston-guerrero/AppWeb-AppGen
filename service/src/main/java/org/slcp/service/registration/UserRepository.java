package org.slcp.service.registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.User;
import org.slcp.service.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las cuentas de usuario. */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsernameIgnoreCase(String username);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmailIgnoreCase(String email);

	Optional<User> findByReadableId(String readableId);

	List<User> findByStatusOrderByCreatedAtAsc(UserStatus status);

	/**
	 * Mayor numero de identificador de cuenta usado.
	 *
	 * <p>Contar no sirve para numerar. Es el mismo defecto que aparecio en los
	 * entregables, y se corrige igual en todas partes: la cuenta baja al eliminar
	 * y devuelve un numero ya tomado; el mayor no vuelve atras.</p>
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'USR-ACC-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM users", nativeQuery = true)
	int mayorNumero();
}
