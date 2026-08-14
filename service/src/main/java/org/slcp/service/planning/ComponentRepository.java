package org.slcp.service.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Component;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComponentRepository extends JpaRepository<Component, UUID> {

	List<Component> findByDeliverableIdOrderByReadableIdAsc(UUID deliverableId);

	List<Component> findByProjectIdOrderByReadableIdAsc(UUID projectId);

	Optional<Component> findByProjectIdAndReadableId(UUID projectId, String readableId);

	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'COM-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM components WHERE project_id = :proyecto", nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId);
}
