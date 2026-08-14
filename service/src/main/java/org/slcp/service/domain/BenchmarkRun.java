package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un ensayo comparativo de proveedores.
 *
 * <p>Se guarda con los requisitos sobre los que se hizo para poder repetirlo
 * igual: un ensayo que no puede repetirse no es evidencia, es una anecdota.</p>
 */
@Entity
@Table(name = "benchmark_runs")
public class BenchmarkRun {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "feature", nullable = false, length = 40, updatable = false)
	private String feature;

	@Column(name = "requirements", nullable = false, updatable = false)
	private String requirements;

	@Column(name = "subkind", length = 40, updatable = false)
	private String subkind;

	@Column(name = "run_by", nullable = false, updatable = false)
	private UUID runBy;

	@Column(name = "run_at", nullable = false, updatable = false)
	private Instant runAt;

	@Column(name = "notes", updatable = false)
	private String notes;

	protected BenchmarkRun() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getFeature() {
		return feature;
	}

	public String getRequirements() {
		return requirements;
	}

	public String getSubkind() {
		return subkind;
	}

	public UUID getRunBy() {
		return runBy;
	}

	public Instant getRunAt() {
		return runAt;
	}

	public String getNotes() {
		return notes;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof BenchmarkRun r && Objects.equals(id, r.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
