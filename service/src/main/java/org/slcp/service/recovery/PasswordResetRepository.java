package org.slcp.service.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a los enlaces de recuperacion. */
public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

	Optional<PasswordReset> findByTokenHash(String tokenHash);

	@Query("""
			SELECT p FROM PasswordReset p
			WHERE p.userId = :userId AND p.usedAt IS NULL AND p.revokedAt IS NULL
			""")
	List<PasswordReset> vigentesDe(UUID userId);
}
