package org.slcp.service.generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.GeneratedArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a las pruebas y diagramas generados. */
public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifact, UUID> {

	List<GeneratedArtifact> findByProjectIdAndKindOrderByReadableIdAsc(UUID projectId, String kind);

	Optional<GeneratedArtifact> findByProjectIdAndReadableId(UUID projectId, String readableId);

	/**
	 * Mayor numero usado, por clase de artefacto.
	 *
	 * <p>Por el mayor y no por la cuenta: contar devuelve un numero ya tomado en
	 * cuanto se elimina uno.</p>
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM '[A-Z]+-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM generated_artifacts WHERE project_id = :proyecto AND kind = :clase",
			nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId, @Param("clase") String kind);

	@Query(value = "SELECT requirement_id FROM artifact_requirements WHERE artifact_id = :artefacto",
			nativeQuery = true)
	List<UUID> requisitosDe(@Param("artefacto") UUID artifactId);

	@Modifying
	@Query(value = "INSERT INTO artifact_requirements (artifact_id, requirement_id, linked_at) "
			+ "VALUES (:artefacto, :requisito, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
	void enlazar(@Param("artefacto") UUID artifactId, @Param("requisito") UUID requirementId);

	/**
	 * Cobertura de pruebas por requisito, calculada por la vista.
	 *
	 * <p>Un requisito esta cubierto cuando tiene al menos una prueba aceptada. Con
	 * pruebas solo propuestas no lo esta: nadie las ha juzgado todavia, y esa
	 * distincion es justamente la que interesa.</p>
	 */
	@Query(value = "SELECT c.requirement_id, c.tests, c.accepted_tests, c.covered "
			+ "FROM requirement_coverage c JOIN requirements r ON r.id = c.requirement_id "
			+ "WHERE r.project_id = :proyecto AND r.status = 'APPROVED'", nativeQuery = true)
	List<Object[]> cobertura(@Param("proyecto") UUID projectId);
}
