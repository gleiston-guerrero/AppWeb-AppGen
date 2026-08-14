package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Horas dedicadas a una actividad.
 *
 * <p>Se registran como asientos y no como un total en la actividad: un total se
 * sobrescribe y pierde cuando se dedico cada hora, que es lo que hace falta para
 * responder si lo hecho corresponde a lo gastado (PRG-09).</p>
 *
 * <p>El asiento no se modifica. Si se anoto mal, se anota otro que lo corrija:
 * asi el registro cuenta lo que ocurrio, incluida la correccion.</p>
 */
@Entity
@Table(name = "time_entries")
public class TimeEntry {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "activity_id", nullable = false, updatable = false)
	private UUID activityId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "person_id", nullable = false, updatable = false)
	private UUID personId;

	@Column(name = "hours", nullable = false, updatable = false)
	private BigDecimal hours;

	@Column(name = "worked_on", nullable = false, updatable = false)
	private LocalDate workedOn;

	@Column(name = "note", updatable = false)
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TimeEntry() {
	}

	public static TimeEntry de(UUID projectId, UUID activityId, UUID personId, BigDecimal hours,
			LocalDate workedOn, String note, Instant momento) {

		if (hours == null || hours.signum() <= 0) {
			throw new IllegalArgumentException("Las horas dedicadas han de ser mayores que cero");
		}
		if (hours.compareTo(new BigDecimal("24")) > 0) {
			throw new IllegalArgumentException(
					"No caben mas de veinticuatro horas en un dia. Reparta el asiento entre los dias "
							+ "en que de verdad se trabajo");
		}

		TimeEntry t = new TimeEntry();
		t.id = UUID.randomUUID();
		t.activityId = activityId;
		t.projectId = projectId;
		t.personId = personId;
		t.hours = hours;
		t.workedOn = workedOn == null ? LocalDate.now() : workedOn;
		t.note = TextNormalizer.enunciado(note);
		t.createdAt = momento;
		return t;
	}

	public UUID getId() {
		return id;
	}

	public UUID getActivityId() {
		return activityId;
	}

	public UUID getPersonId() {
		return personId;
	}

	public BigDecimal getHours() {
		return hours;
	}

	public LocalDate getWorkedOn() {
		return workedOn;
	}

	public String getNote() {
		return note;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof TimeEntry t && Objects.equals(id, t.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
