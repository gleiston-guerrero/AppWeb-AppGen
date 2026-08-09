package org.slcp.service.projects;

import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.MembershipStatus;
import org.slcp.service.domain.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las membresias. */
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {

	List<ProjectMembership> findByProjectIdAndStatus(UUID projectId, MembershipStatus status);

	List<ProjectMembership> findByProjectIdAndUserIdAndStatus(UUID projectId, UUID userId, MembershipStatus status);

	List<ProjectMembership> findByUserIdAndStatus(UUID userId, MembershipStatus status);
}
