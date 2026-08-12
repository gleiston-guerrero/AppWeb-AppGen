package org.slcp.service.deliverables;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los entregables y a su enlace con los requisitos. */
public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {

	List<Deliverable> findByProjectIdOrderByReadableIdAsc(UUID projectId);

	Optional<Deliverable> findByProjectIdAndReadableId(UUID projectId, String readableId);

	long countByProjectId(UUID projectId);

	/**
	 * Mayor numero de identificador usado en el proyecto.
	 *
	 * <p>Se consulta el mayor y no la cuenta: si se elimino un entregable, la
	 * cuenta devuelve un numero ya usado y el alta choca contra la restriccion de
	 * unicidad. El mayor no vuelve atras.</p>
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'ENT-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM deliverables WHERE project_id = :proyecto", nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId);

	/**
	 * El enlace se maneja con consultas directas y no con una entidad propia.
	 *
	 * <p>Es una tabla de union sin datos propios mas alla de la fecha, y modelarla
	 * como entidad anadiria un ciclo entre agregados sin aportar nada.</p>
	 */
	@Query(value = "SELECT requirement_id FROM deliverable_requirements WHERE deliverable_id = :id",
			nativeQuery = true)
	List<UUID> requisitosDe(@Param("id") UUID deliverableId);

	@Query(value = "SELECT deliverable_id FROM deliverable_requirements WHERE requirement_id = :id",
			nativeQuery = true)
	List<UUID> entregablesDe(@Param("id") UUID requirementId);

	@Modifying
	@Query(value = "INSERT INTO deliverable_requirements (deliverable_id, requirement_id, linked_at) "
			+ "VALUES (:entregable, :requisito, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
	void enlazar(@Param("entregable") UUID deliverableId, @Param("requisito") UUID requirementId);

	@Modifying
	@Query(value = "DELETE FROM deliverable_requirements "
			+ "WHERE deliverable_id = :entregable AND requirement_id = :requisito", nativeQuery = true)
	void desenlazar(@Param("entregable") UUID deliverableId, @Param("requisito") UUID requirementId);

	/**
	 * Requisitos cerrados del proyecto, calculados por la vista (RQM-14).
	 *
	 * <p>Se consulta la vista y no se recalcula aqui: dos calculos del mismo hecho
	 * acaban discrepando, y entonces ninguno es fiable.</p>
	 */
	@Query(value = "SELECT c.requirement_id FROM requirement_closure c "
			+ "JOIN requirements r ON r.id = c.requirement_id "
			+ "WHERE r.project_id = :proyecto AND c.closed", nativeQuery = true)
	List<UUID> requisitosCerrados(@Param("proyecto") UUID projectId);
}
