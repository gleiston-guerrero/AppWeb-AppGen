package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entregable: resultado que alguien recibe y acepta.
 *
 * <p>Es el nivel 2 de la descomposicion de SLCP-DOC-018 y realiza uno o varios
 * requisitos aprobados. Su aceptacion es lo que permite calcular el cierre de
 * esos requisitos (RQM-14).</p>
 */
@Entity
@Table(name = "deliverables")
public class Deliverable {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Column(name = "description")
	private String description;

	/** Que ha de comprobar quien lo recibe para darlo por bueno. */
	@Column(name = "acceptance")
	private String acceptance;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DeliverableStatus status;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "ever_decided", nullable = false)
	private boolean everDecided;

	@Column(name = "accepted_by")
	private UUID acceptedBy;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Deliverable() {
	}

	public static Deliverable crear(UUID projectId, String readableId, String name,
			String description, String acceptance, UUID createdBy, Instant momento) {

		Deliverable d = new Deliverable();
		d.id = UUID.randomUUID();
		d.readableId = readableId;
		d.projectId = projectId;
		d.name = name.trim();
		d.description = limpiar(description);
		d.acceptance = limpiar(acceptance);
		d.status = DeliverableStatus.PLANNED;
		d.version = 1;
		d.createdBy = createdBy;
		d.createdAt = momento;
		d.updatedAt = momento;
		return d;
	}

	/**
	 * Modifica los datos y devuelve el entregable a su estado inicial.
	 *
	 * <p>Lo entregado o aceptado lo fue sobre otra definicion; darla por vigente
	 * sobre la nueva seria atribuir a quien acepto algo que no recibio.</p>
	 */
	public void editar(String name, String description, String acceptance, Instant momento) {
		if (!status.admiteEdicion()) {
			throw new IllegalStateException(
					"Un entregable aceptado no se modifica. Formule una peticion de cambio");
		}
		if (name != null && !name.isBlank()) {
			this.name = name.trim();
		}
		this.description = limpiar(description);
		this.acceptance = limpiar(acceptance);
		this.status = DeliverableStatus.PLANNED;
		this.acceptedBy = null;
		this.acceptedAt = null;
		this.version++;
		this.updatedAt = momento;
	}

	public void transitarA(DeliverableStatus destino, UUID autor, Instant momento) {
		if (!status.puedeTransitarA(destino)) {
			throw new IllegalStateException("Transicion no admitida de " + status + " a " + destino);
		}
		if (destino == DeliverableStatus.ACCEPTED) {
			this.acceptedBy = autor;
			this.acceptedAt = momento;
		}
		if (destino == DeliverableStatus.DELIVERED || destino == DeliverableStatus.ACCEPTED) {
			this.everDecided = true;
		}
		this.status = destino;
		this.updatedAt = momento;
	}

	/** Solo mientras nada se haya entregado ni aceptado sobre el. */
	public boolean puedeEliminarse() {
		return !everDecided;
	}

	private static String limpiar(String valor) {
		if (valor == null) {
			return null;
		}
		String t = valor.trim();
		return t.isEmpty() ? null : t;
	}

	public UUID getId() {
		return id;
	}

	public String getReadableId() {
		return readableId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getAcceptance() {
		return acceptance;
	}

	public DeliverableStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public UUID getAcceptedBy() {
		return acceptedBy;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Deliverable d && Objects.equals(id, d.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
