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
 * Prueba o diagrama generado a partir de los requisitos.
 *
 * <p>Nace propuesto y nunca aceptado. Lo que sale de un generador es una
 * redaccion que alguien ha de juzgar: que la escriba la plataforma o un modelo
 * no la vuelve correcta, y darla por buena sola quitaria a quien responde del
 * sistema la unica ocasion de leerla.</p>
 *
 * <p>Se guarda su procedencia porque no es lo mismo lo que se dedujo del
 * enunciado que lo que redacto un modelo, y quien revise dentro de un ano tiene
 * derecho a saber cual esta leyendo.</p>
 */
@Entity
@Table(name = "generated_artifacts")
public class GeneratedArtifact {

	/** Estados. Propuesto es el unico en que puede nacer. */
	public static final String PROPUESTO = "PROPOSED";
	public static final String ACEPTADO = "ACCEPTED";
	public static final String DESCARTADO = "DISCARDED";

	/** Procedencias. */
	public static final String DERIVADO = "DERIVED";
	public static final String ASISTIDO = "ASSISTED";
	public static final String HUMANO = "HUMAN";

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "kind", nullable = false, length = 20, updatable = false)
	private String kind;

	@Column(name = "subkind", nullable = false, length = 40)
	private String subkind;

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Column(name = "content", nullable = false)
	private String content;

	@Column(name = "format", nullable = false, length = 20)
	private String format;

	@Column(name = "origin", nullable = false, length = 20)
	private String origin;

	@Column(name = "rationale")
	private String rationale;

	@Column(name = "needs_decision", nullable = false)
	private boolean needsDecision;

	/** Se acepto teniendo huecos pendientes. La decision del equipo prevalece. */
	@Column(name = "accepted_with_gaps", nullable = false)
	private boolean acceptedWithGaps;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "reviewed_by")
	private UUID reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	/**
	 * Propietario del producto que lo dio por revisado.
	 *
	 * <p>No es una aprobacion y no altera el estado. Aprobar lo generado
	 * corresponde al equipo, que es quien va a ejecutarlo; el propietario deja
	 * constancia de haberlo visto, y puede hacerlo antes o despues.</p>
	 */
	@Column(name = "owner_reviewed_by")
	private UUID ownerReviewedBy;

	@Column(name = "owner_reviewed_at")
	private Instant ownerReviewedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected GeneratedArtifact() {
	}

	public static GeneratedArtifact crear(UUID projectId, String readableId, String kind,
			String subkind, String title, String content, String format, String origin,
			String rationale, boolean needsDecision, UUID createdBy, Instant momento) {

		GeneratedArtifact a = new GeneratedArtifact();
		a.id = UUID.randomUUID();
		a.readableId = readableId;
		a.projectId = projectId;
		a.kind = kind;
		a.subkind = subkind;
		a.title = TextNormalizer.nombre(title);
		a.content = content;
		a.format = format;
		a.origin = origin;
		a.rationale = rationale;
		a.needsDecision = needsDecision;
		a.status = PROPUESTO;
		a.version = 1;
		a.createdBy = createdBy;
		a.createdAt = momento;
		a.updatedAt = momento;
		return a;
	}

	/**
	 * Modifica el contenido.
	 *
	 * <p>Editarlo lo devuelve a propuesto y lo marca como humano: lo aceptado lo
	 * fue sobre otro texto, y quien lo modifica pasa a responder de el aunque
	 * partiera de lo generado.</p>
	 */
	public void editar(String title, String content, Instant momento) {
		if (title != null && !title.isBlank()) {
			this.title = TextNormalizer.nombre(title);
		}
		if (content != null && !content.isBlank()) {
			this.content = content;
			this.origin = HUMANO;
			// Quien lo edita puede haber rellenado los huecos; si quedan, se sabra.
			this.needsDecision = content.contains("[indique");
		}
		this.status = PROPUESTO;
		this.reviewedBy = null;
		this.reviewedAt = null;

		// La revision del propietario tambien se retira: vio otro texto, y darlo
		// por revisado sobre el nuevo seria atribuirle algo que no leyo.
		this.ownerReviewedBy = null;
		this.ownerReviewedAt = null;

		this.version++;
		this.updatedAt = momento;
	}

	/**
	 * Lo acepta un miembro del equipo, y consta cual.
	 *
	 * <p>Se acepta aunque tenga huecos. Impedirlo convertiria una advertencia en un
	 * veto, y pondria a la plataforma por encima de quien responde del sistema:
	 * puede haber razones para dar por bueno algo incompleto --- que el hueco se
	 * resuelva en otro artefacto, que no aplique a este caso --- y quien las conoce
	 * es el equipo, no una regla escrita de antemano.</p>
	 *
	 * <p>Lo que si queda es constancia de que se acepto habiendo reparos. Aceptar
	 * sobre un aviso es legitimo; que despues no se sepa que lo habia, no.</p>
	 */
	public void aceptar(UUID revisor, Instant momento) {
		this.acceptedWithGaps = needsDecision;
		this.status = ACEPTADO;
		this.reviewedBy = revisor;
		this.reviewedAt = momento;
		this.updatedAt = momento;
	}

	/**
	 * El propietario del producto lo da por revisado, o retira su revision.
	 *
	 * <p>No toca el estado ni la aprobacion del equipo: son dos actos distintos.
	 * Uno dice que esto sirve para probar el sistema; el otro, que quien pidio el
	 * sistema ha visto lo que se va a probar.</p>
	 */
	public void darPorRevisado(boolean revisado, UUID propietario, Instant momento) {
		this.ownerReviewedBy = revisado ? propietario : null;
		this.ownerReviewedAt = revisado ? momento : null;
		this.updatedAt = momento;
	}

	public UUID getOwnerReviewedBy() {
		return ownerReviewedBy;
	}

	public Instant getOwnerReviewedAt() {
		return ownerReviewedAt;
	}

	public void descartar(UUID revisor, Instant momento) {
		this.status = DESCARTADO;
		this.reviewedBy = revisor;
		this.reviewedAt = momento;
		this.updatedAt = momento;
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

	public String getSubkind() {
		return subkind;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getFormat() {
		return format;
	}

	public String getOrigin() {
		return origin;
	}

	public String getRationale() {
		return rationale;
	}

	public boolean isNeedsDecision() {
		return needsDecision;
	}

	public boolean isAcceptedWithGaps() {
		return acceptedWithGaps;
	}

	public String getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public UUID getReviewedBy() {
		return reviewedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof GeneratedArtifact a && Objects.equals(id, a.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
