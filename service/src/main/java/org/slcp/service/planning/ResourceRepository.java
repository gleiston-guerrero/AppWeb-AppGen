package org.slcp.service.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

	List<Resource> findByProjectIdOrderByReadableIdAsc(UUID projectId);

	Optional<Resource> findByProjectIdAndReadableId(UUID projectId, String readableId);

	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'REC-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM resources WHERE project_id = :proyecto", nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId);

	@Query(value = "SELECT COUNT(*) FROM task_resources WHERE resource_id = :recurso",
			nativeQuery = true)
	long asignaciones(@Param("recurso") UUID resourceId);
}
