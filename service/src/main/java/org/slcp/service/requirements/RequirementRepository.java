package org.slcp.service.requirements;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los requisitos. */
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

	List<Requirement> findByProjectIdOrderByReadableIdAsc(UUID projectId);

	List<Requirement> findByProjectIdAndStatusOrderByReadableIdAsc(UUID projectId, RequirementStatus status);

	Optional<Requirement> findByProjectIdAndReadableId(UUID projectId, String readableId);

	Optional<Requirement> findByProjectIdAndSourceId(UUID projectId, String sourceId);

	long countByProjectId(UUID projectId);

	/**
	 * Mayor numero de identificador usado en el proyecto.
	 *
	 * <p>Contar no sirve para numerar: si se elimino un requisito, la cuenta
	 * devuelve un numero ya usado.</p>
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'REQ-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM requirements WHERE project_id = :proyecto", nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId);
}
