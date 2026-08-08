package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento del almacen de solo anexado.
 *
 * <p>Realiza TRC-24. La entidad carece de metodos de modificacion de forma
 * deliberada: no existe operacion que actualice un evento una vez registrado.
 * Tampoco lleva clave foranea, para que un evento sobreviva a la desaparicion
 * de cualquier otra fila.</p>
 */
@Entity
@Table(name = "event_records")
public class EventRecord {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "event_type", nullable = false, updatable = false, length = 60)
	private String eventType;

	@Column(name = "subject_type", nullable = false, updatable = false, length = 60)
	private String subjectType;

	@Column(name = "subject_id", nullable = false, updatable = false)
	private UUID subjectId;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Column(name = "actor_label", nullable = false, updatable = false, length = 160)
	private String actorLabel;

	@Column(name = "payload", nullable = false, updatable = false)
	private String payload;

	/** Constructor exigido por JPA. */
	protected EventRecord() {
	}

	public static EventRecord de(String eventType, String subjectType, UUID subjectId,
			UUID actorId, String actorLabel, String payload, Instant momento) {
		EventRecord evento = new EventRecord();
		evento.id = UUID.randomUUID();
		evento.occurredAt = momento;
		evento.eventType = eventType;
		evento.subjectType = subjectType;
		evento.subjectId = subjectId;
		evento.actorId = actorId;
		evento.actorLabel = actorLabel;
		evento.payload = payload;
		return evento;
	}

	public UUID getId() {
		return id;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getEventType() {
		return eventType;
	}

	public UUID getSubjectId() {
		return subjectId;
	}

	public String getActorLabel() {
		return actorLabel;
	}

	public String getPayload() {
		return payload;
	}
}
