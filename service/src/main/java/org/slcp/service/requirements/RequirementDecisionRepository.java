package org.slcp.service.requirements;

import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.RequirementDecision;
import org.springframework.data.jpa.repository.JpaRepository;

/** Historial de decisiones sobre requisitos. */
public interface RequirementDecisionRepository extends JpaRepository<RequirementDecision, UUID> {

	List<RequirementDecision> findByRequirementIdOrderByVersionAscDecidedAtAsc(UUID requirementId);
}
