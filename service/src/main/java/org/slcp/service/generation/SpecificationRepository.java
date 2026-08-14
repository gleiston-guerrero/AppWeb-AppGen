package org.slcp.service.generation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los casos de uso e historias. */
public interface SpecificationRepository extends JpaRepository<Specification, UUID> {

	List<Specification> findByProjectIdAndKindOrderByReadableIdAsc(UUID projectId, String kind);

	Optional<Specification> findByProjectIdAndReadableId(UUID projectId, String readableId);

	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM '[A-Z]+-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM specifications WHERE project_id = :proyecto AND kind = :clase",
			nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId, @Param("clase") String kind);

	@Query(value = "SELECT requirement_id FROM specification_requirements "
			+ "WHERE specification_id = :spec", nativeQuery = true)
	List<UUID> requisitosDe(@Param("spec") UUID specificationId);

	@Modifying
	@Query(value = "INSERT INTO specification_requirements "
			+ "(specification_id, requirement_id, requirement_version, linked_at) "
			+ "VALUES (:spec, :requisito, :version, now()) "
			+ "ON CONFLICT (specification_id, requirement_id) "
			+ "DO UPDATE SET requirement_version = :version", nativeQuery = true)
	void enlazar(@Param("spec") UUID specificationId, @Param("requisito") UUID requirementId,
			@Param("version") int requirementVersion);

	/**
	 * Actualiza la version guardada a la actual de cada requisito.
	 *
	 * <p>Se hace al aceptar: la regla base vale para el texto que se acepto, y
	 * desde ahi se compara.</p>
	 */
	@Modifying
	@Query(value = "UPDATE specification_requirements sr SET requirement_version = r.version "
			+ "FROM requirements r WHERE r.id = sr.requirement_id AND sr.specification_id = :spec",
			nativeQuery = true)
	void refrescarVersiones(@Param("spec") UUID specificationId);

	/** Si algun requisito cambio desde que se acepto. */
	@Query(value = "SELECT COALESCE(bool_or(r.version <> sr.requirement_version), FALSE) "
			+ "FROM specification_requirements sr JOIN requirements r ON r.id = sr.requirement_id "
			+ "WHERE sr.specification_id = :spec", nativeQuery = true)
	boolean estaAtrasada(@Param("spec") UUID specificationId);

	/**
	 * Requisitos que ya tienen una regla base vigente de esa clase.
	 *
	 * <p>Se excluyen al generar: regenerar sobre lo aceptado borraria el trabajo de
	 * quien lo reviso. Los atrasados no cuentan como vigentes.</p>
	 */
	@Query(value = "SELECT sr.requirement_id FROM specification_requirements sr "
			+ "JOIN specifications s ON s.id = sr.specification_id "
			+ "JOIN requirements r ON r.id = sr.requirement_id "
			+ "WHERE s.project_id = :proyecto AND s.kind = :clase AND s.is_baseline "
			+ "AND r.version = sr.requirement_version", nativeQuery = true)
	List<UUID> requisitosConReglaBaseVigente(@Param("proyecto") UUID projectId,
			@Param("clase") String kind);

	// --- Reparos de la comprobacion ---

	@Modifying
	@Query(value = "DELETE FROM specification_issues WHERE specification_id = :spec",
			nativeQuery = true)
	void borrarReparos(@Param("spec") UUID specificationId);

	@Modifying
	@Query(value = "INSERT INTO specification_issues "
			+ "(id, specification_id, field, reason, severe, source, raised_at) "
			+ "VALUES (:id, :spec, :campo, :motivo, :grave, :fuente, :momento)", nativeQuery = true)
	void anadirReparo(@Param("id") UUID id, @Param("spec") UUID specificationId,
			@Param("campo") String field, @Param("motivo") String reason,
			@Param("grave") boolean severe, @Param("fuente") String source,
			@Param("momento") Instant raisedAt);

	@Query(value = "SELECT COUNT(*) FROM specification_issues WHERE specification_id = :spec",
			nativeQuery = true)
	int reparosDe(@Param("spec") UUID specificationId);

	@Query(value = "SELECT field, reason, severe, source FROM specification_issues "
			+ "WHERE specification_id = :spec ORDER BY severe DESC, field", nativeQuery = true)
	List<Object[]> reparosVistaDe(@Param("spec") UUID specificationId);
}
