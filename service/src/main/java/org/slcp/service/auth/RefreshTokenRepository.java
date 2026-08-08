package org.slcp.service.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los tokens de renovacion. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);
}
