package org.slcp.service.generation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los ensayos comparativos y a sus resultados. */
public interface BenchmarkRepository extends JpaRepository<BenchmarkRun, UUID> {

	List<BenchmarkRun> findByProjectIdOrderByRunAtDesc(UUID projectId);

	@Modifying
	@Query(value = "INSERT INTO benchmark_runs "
			+ "(id, project_id, feature, requirements, subkind, run_by, run_at, notes, prompt_used) "
			+ "VALUES (:id, :proyecto, :funcion, :requisitos, :subclase, :autor, :momento, :notas, "
			+ ":instruccion)",
			nativeQuery = true)
	void crearEnsayo(@Param("id") UUID id, @Param("proyecto") UUID projectId,
			@Param("funcion") String feature, @Param("requisitos") String requirements,
			@Param("subclase") String subkind, @Param("autor") UUID runBy,
			@Param("momento") Instant runAt, @Param("notas") String notes,
			@Param("instruccion") String promptUsed);

	@Modifying
	@Query(value = "INSERT INTO benchmark_results "
			+ "(id, run_id, provider, model, produced, complete, invented, issues, elapsed_ms, "
			+ "failed, sample) "
			+ "VALUES (:id, :ensayo, :proveedor, :modelo, :producidas, :completas, :inventadas, "
			+ ":reparos, :tiempo, FALSE, :muestra)", nativeQuery = true)
	void anotarResultado(@Param("id") UUID id, @Param("ensayo") UUID runId,
			@Param("proveedor") String provider, @Param("modelo") String model,
			@Param("producidas") int produced, @Param("completas") int complete,
			@Param("inventadas") int invented, @Param("reparos") int issues,
			@Param("tiempo") int elapsedMs, @Param("muestra") String sample);

	/**
	 * Anota que un proveedor fallo.
	 *
	 * <p>Un fallo es un resultado: si no se anotara, quien pidio comparar tres
	 * proveedores veria dos columnas sin saber que paso con la tercera.</p>
	 */
	@Modifying
	@Query(value = "INSERT INTO benchmark_results "
			+ "(id, run_id, provider, model, failed, failure_reason) "
			+ "VALUES (:id, :ensayo, :proveedor, :modelo, TRUE, :motivo)", nativeQuery = true)
	void anotarFallo(@Param("id") UUID id, @Param("ensayo") UUID runId,
			@Param("proveedor") String provider, @Param("modelo") String model,
			@Param("motivo") String failureReason);

	@Query(value = "SELECT provider, model, produced, complete, invented, issues, elapsed_ms, "
			+ "failed, failure_reason, sample FROM benchmark_results "
			+ "WHERE run_id = :ensayo ORDER BY failed, complete DESC, invented, elapsed_ms",
			nativeQuery = true)
	List<Object[]> resultadosDe(@Param("ensayo") UUID runId);
}
