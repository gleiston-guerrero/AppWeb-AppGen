package org.slcp.service.projects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a los proyectos. */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

	Optional<Project> findByReadableId(String readableId);

	/**
	 * Proyectos donde la persona tiene membresia activa.
	 *
	 * <p>Es la consulta que sostiene RPT-04: nadie ve un proyecto en el que no
	 * participa, y el alcance se deriva de la membresia en lugar de configurarse
	 * por separado.</p>
	 */
	@Query("""
			SELECT p FROM Project p
			WHERE p.id IN (
			    SELECT m.projectId FROM ProjectMembership m
			    WHERE m.userId = :userId AND m.status = org.slcp.service.domain.MembershipStatus.ACTIVE
			)
			ORDER BY p.createdAt DESC
			""")
	List<Project> deLaPersona(UUID userId);

	/**
	 * Mayor numero de identificador usado.
	 *
	 * <p>Contar no sirve para numerar: si se elimino un proyecto, la cuenta
	 * devuelve un numero ya tomado y el alta choca contra la unicidad. Es el mismo
	 * defecto que aparecio en los entregables, y se corrige igual en todas
	 * partes.</p>
	 */
	@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(readable_id FROM 'PRJ-([0-9]+)') AS INTEGER)), 0) "
			+ "FROM projects", nativeQuery = true)
	int mayorNumero();
}
