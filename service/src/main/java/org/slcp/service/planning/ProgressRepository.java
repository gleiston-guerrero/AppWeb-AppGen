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

/**
 * Avance calculado, consultado a las vistas de la base de datos.
 *
 * <p>No se recalcula aqui: dos calculos del mismo hecho acaban discrepando, y
 * entonces ninguno es fiable (PRG-10).</p>
 */
public interface ProgressRepository extends JpaRepository<Component, UUID> {

	@Query(value = "SELECT task_id, effort, progress, spent_hours FROM task_progress "
			+ "WHERE task_id IN (SELECT id FROM tasks WHERE project_id = :proyecto)",
			nativeQuery = true)
	List<Object[]> tareas(@Param("proyecto") UUID projectId);

	@Query(value = "SELECT component_id, effort, progress, spent_hours, tasks "
			+ "FROM component_progress WHERE component_id IN "
			+ "(SELECT id FROM components WHERE project_id = :proyecto)", nativeQuery = true)
	List<Object[]> componentes(@Param("proyecto") UUID projectId);

	@Query(value = "SELECT deliverable_id, effort, progress, spent_hours, components "
			+ "FROM deliverable_progress WHERE deliverable_id IN "
			+ "(SELECT id FROM deliverables WHERE project_id = :proyecto)", nativeQuery = true)
	List<Object[]> entregables(@Param("proyecto") UUID projectId);

	@Query(value = "SELECT effort, progress, spent_hours, deliverables FROM project_progress "
			+ "WHERE project_id = :proyecto", nativeQuery = true)
	List<Object[]> proyecto(@Param("proyecto") UUID projectId);
}
