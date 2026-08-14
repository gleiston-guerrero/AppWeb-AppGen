package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso expandido o historia de usuario.
 *
 * <p>Los campos son los de las tablas 8 y 9 del manuscrito y se guardan como
 * documento: los flujos son listas de pasos a dos columnas y las historias
 * tienen nueve campos opcionales, de modo que una columna por campo daria veinte
 * casi siempre nulas.</p>
 *
 * <p>Lo que el equipo acepta pasa a ser <strong>regla base</strong>: se conserva
 * aunque se vuelva a generar. Sobrescribirlo borraria el trabajo de quien lo
 * reviso, y entonces nadie revisaria nada.</p>
 */
@Entity
@Table(name = "specifications")
public class Specification {

	public static final String CASO_DE_USO = "USE_CASE";
	public static final String HISTORIA = "USER_STORY";

	public static final String GENERADO = "AI_GENERATED";
	public static final String EDITADO = "AI_EDITED";
	public static final String HUMANO = "HUMAN";

	public static final String BORRADOR = "DRAFT";
	public static final String PROPUESTA = "PROPOSED";
	public static final String ACEPTADA = "ACCEPTED";
	public static final String DESCARTADA = "DISCARDED";

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "kind", nullable = false, length = 20, updatable = false)
	private String kind;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Column(name = "fields", nullable = false, columnDefinition = "jsonb")
	private String fields;

	@Column(name = "origin", nullable = false, length = 20)
	private String origin;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "is_baseline", nullable = false)
	private boolean baseline;

	@Column(name = "stale", nullable = false)
	private boolean stale;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "accepted_with_issues", nullable = false)
	private int acceptedWithIssues;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "accepted_by")
	private UUID acceptedBy;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Specification() {
	}

	public static Specification crear(UUID projectId, String readableId, String kind, String name,
			String fields, String origin, UUID createdBy, Instant momento) {

		Specification s = new Specification();
		s.id = UUID.randomUUID();
		s.readableId = readableId;
		s.projectId = projectId;
		s.kind = kind;
		s.name = TextNormalizer.nombre(name);
		s.fields = fields;
		s.origin = origin;
		s.status = PROPUESTA;
		s.version = 1;
		s.createdBy = createdBy;
		s.createdAt = momento;
		s.updatedAt = momento;
		return s;
	}

	/**
	 * Modifica el contenido.
	 *
	 * <p>Retira la regla base y devuelve a propuesta: lo aceptado lo fue sobre otro
	 * texto. Y pasa a constar editada, porque quien la modifica responde de ella
	 * aunque partiera de lo generado.</p>
	 */
	public void editar(String name, String fields, Instant momento) {
		if (name != null && !name.isBlank()) {
			this.name = TextNormalizer.nombre(name);
		}
		if (fields != null && !fields.isBlank()) {
			this.fields = fields;
			this.origin = GENERADO.equals(this.origin) ? EDITADO : this.origin;
		}

		this.status = PROPUESTA;
		this.baseline = false;
		this.stale = false;
		this.acceptedBy = null;
		this.acceptedAt = null;
		this.acceptedWithIssues = 0;
		this.version++;
		this.updatedAt = momento;
	}

	/**
	 * El equipo la acepta y pasa a ser regla base.
	 *
	 * <p>Se acepta aunque tenga reparos: la comprobacion informa y decide el
	 * equipo. Lo que queda es constancia de cuantos habia.</p>
	 */
	public void aceptar(UUID revisor, int reparos, Instant momento) {
		this.status = ACEPTADA;
		this.baseline = true;
		this.stale = false;
		this.acceptedBy = revisor;
		this.acceptedAt = momento;
		this.acceptedWithIssues = reparos;
		this.updatedAt = momento;
	}

	public void descartar(Instant momento) {
		this.status = DESCARTADA;
		this.baseline = false;
		this.updatedAt = momento;
	}

	/** Retira la condicion de regla base para poder regenerar. */
	public void retirarReglaBase(Instant momento) {
		this.baseline = false;
		this.stale = false;
		this.updatedAt = momento;
	}

	public boolean esReglaBase() {
		return baseline;
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

	public String getKind() {
		return kind;
	}

	public String getName() {
		return name;
	}

	public String getFields() {
		return fields;
	}

	public String getOrigin() {
		return origin;
	}

	public String getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public int getAcceptedWithIssues() {
		return acceptedWithIssues;
	}

	public UUID getAcceptedBy() {
		return acceptedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Specification s && Objects.equals(id, s.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
