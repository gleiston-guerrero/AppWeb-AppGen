package org.slcp.service.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

	List<Task> findByComponentIdOrderByReadableIdAsc(UUID componentId);

	List<Task> findByProjectIdOrderByReadableIdAsc(UUID projectId);

	List<Task> findByProjectIdAndAssigneeIdOrderByReadableIdAsc(UUID projectId, UUID assigneeId);

	Optional<Task> findByProjectIdAndReadableId(UUID projectId, String readableId);

	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'TAR-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM tasks WHERE project_id = :proyecto", nativeQuery = true)
	int mayorNumero(@Param("proyecto") UUID projectId);

	@Query(value = "SELECT resource_id FROM task_resources WHERE task_id = :tarea",
			nativeQuery = true)
	List<UUID> recursosDe(@Param("tarea") UUID taskId);

	@Modifying
	@Query(value = "INSERT INTO task_resources (task_id, resource_id, quantity, assigned_at) "
			+ "VALUES (:tarea, :recurso, :cantidad, now()) "
			+ "ON CONFLICT (task_id, resource_id) DO UPDATE SET quantity = :cantidad",
			nativeQuery = true)
	void asignarRecurso(@Param("tarea") UUID taskId, @Param("recurso") UUID resourceId,
			@Param("cantidad") BigDecimal quantity);

	@Modifying
	@Query(value = "DELETE FROM task_resources WHERE task_id = :tarea AND resource_id = :recurso",
			nativeQuery = true)
	void retirarRecurso(@Param("tarea") UUID taskId, @Param("recurso") UUID resourceId);
}
