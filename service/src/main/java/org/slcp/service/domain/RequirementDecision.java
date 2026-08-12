package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Decision tomada sobre una version de un requisito.
 *
 * <p>Se conserva aunque el requisito se modifique despues. Modificar olvida la
 * revision vigente --- lo revisado era otro texto ---, pero no debe borrar el
 * hecho de que alguien la hizo: sin este asiento no podria responderse quien
 * reviso la version anterior ni sobre que texto.</p>
 */
@Entity
@Table(name = "requirement_decisions")
public class RequirementDecision {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "requirement_id", nullable = false, updatable = false)
	private UUID requirementId;

	@Column(name = "version", nullable = false, updatable = false)
	private int version;

	@Column(name = "decision", nullable = false, length = 20, updatable = false)
	private String decision;

	@Column(name = "actor_id", nullable = false, updatable = false)
	private UUID actorId;

	@Column(name = "actor_label", nullable = false, length = 120, updatable = false)
	private String actorLabel;

	@Column(name = "decided_at", nullable = false, updatable = false)
	private Instant decidedAt;

	@Column(name = "statement", nullable = false, updatable = false)
	private String statement;

	protected RequirementDecision() {
	}

	public static RequirementDecision de(UUID requirementId, int version, String decision,
			UUID actorId, String actorLabel, String statement, Instant momento) {

		RequirementDecision d = new RequirementDecision();
		d.id = UUID.randomUUID();
		d.requirementId = requirementId;
		d.version = version;
		d.decision = decision;
		d.actorId = actorId;
		d.actorLabel = actorLabel;
		d.statement = statement;
		d.decidedAt = momento;
		return d;
	}

	public int getVersion() {
		return version;
	}

	public String getDecision() {
		return decision;
	}

	public String getActorLabel() {
		return actorLabel;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getStatement() {
		return statement;
	}
}
