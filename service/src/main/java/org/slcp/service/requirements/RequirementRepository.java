package org.slcp.service.requirements;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los requisitos. */
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

	List<Requirement> findByProjectIdOrderByReadableIdAsc(UUID projectId);

	List<Requirement> findByProjectIdAndStatusOrderByReadableIdAsc(UUID projectId, RequirementStatus status);

	Optional<Requirement> findByProjectIdAndReadableId(UUID projectId, String readableId);

	Optional<Requirement> findByProjectIdAndSourceId(UUID projectId, String sourceId);

	long countByProjectId(UUID projectId);
}
