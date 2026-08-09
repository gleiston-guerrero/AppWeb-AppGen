package org.slcp.service.invitations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las invitaciones. */
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	Optional<Invitation> findByTokenHash(String tokenHash);

	@Query("""
			SELECT i FROM Invitation i
			WHERE i.projectId = :projectId AND i.consumedAt IS NULL AND i.revokedAt IS NULL
			ORDER BY i.createdAt DESC
			""")
	List<Invitation> vigentesDelProyecto(UUID projectId);

	@Query("""
			SELECT i FROM Invitation i
			WHERE LOWER(i.email) = LOWER(:email) AND i.consumedAt IS NULL AND i.revokedAt IS NULL
			ORDER BY i.createdAt DESC
			""")
	List<Invitation> vigentesPara(String email);
}
