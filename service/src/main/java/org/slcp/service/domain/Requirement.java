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

/** Requisito de un proyecto. */
@Entity
@Table(name = "requirements")
public class Requirement {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "readable_id", nullable = false, length = 40)
	private String readableId;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "source_id", length = 40)
	private String sourceId;

	@Column(name = "source_line")
	private Integer sourceLine;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20)
	private RequirementKind kind;

	@Column(name = "name", length = 300)
	private String name;

	@Column(name = "statement", nullable = false)
	private String statement;

	@Column(name = "verification")
	private String verification;

	@Column(name = "priority", length = 20)
	private String priority;

	@Column(name = "actor", length = 200)
	private String actor;

	@Column(name = "notes")
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private RequirementStatus status;

	@Column(name = "version", nullable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	@Column(name = "statement_origin", nullable = false, length = 20)
	private TextOrigin statementOrigin;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_origin", nullable = false, length = 20)
	private TextOrigin verificationOrigin;

	/**
	 * Quien realizo la revision previa.
	 *
	 * <p>Se guarda para poder impedir que la misma persona apruebe lo que reviso.
	 * Sin este dato, la doble etapa de RQM-05 se cumpliria de nombre y no de
	 * hecho en cuanto alguien tuviera los dos roles.</p>
	 */
	@Column(name = "reviewed_by")
	private UUID reviewedBy;

	/** Marca de que el requisito llego alguna vez a revisarse o aprobarse. */
	@Column(name = "ever_decided", nullable = false)
	private boolean everDecided;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected Requirement() {
	}

	public static Requirement crear(UUID projectId, String readableId, String sourceId,
			Integer sourceLine, RequirementKind kind, String name, String statement,
			String verification, UUID createdBy, Instant momento) {

		return crear(projectId, readableId, sourceId, sourceLine, kind, name, statement,
				verification, null, createdBy, momento);
	}

	/**
	 * Alta con actor.
	 *
	 * <p>Este campo guarda el <strong>interesado o la fuente</strong> que trae la
	 * especificacion: quien pidio el requisito, o de donde vienen sus datos. No es
	 * el actor de un caso de uso.</p>
	 *
	 * <p>Son cosas distintas y confundirlas produce diagramas falsos: en una
	 * especificacion real este campo trae cosas como "Sensor de humedad" o
	 * "Pasarela de campo", que son origenes de datos y no quien ejerce nada. El
	 * actor de caso de uso se identifica del enunciado, en ActorExtractor.</p>
	 */
	public static Requirement crear(UUID projectId, String readableId, String sourceId,
			Integer sourceLine, RequirementKind kind, String name, String statement,
			String verification, String actor, UUID createdBy, Instant momento) {

		Requirement r = new Requirement();
		r.id = UUID.randomUUID();
		r.readableId = readableId;
		r.projectId = projectId;
		r.sourceId = (sourceId == null || sourceId.isBlank()) ? null : sourceId.trim();
		r.sourceLine = sourceLine;
		r.kind = kind;
		r.name = TextNormalizer.nombre(name);
		r.statement = statement == null ? "" : statement.trim();
		r.verification = TextNormalizer.enunciado(verification);
		r.actor = actor == null || actor.isBlank() ? null : actor.trim();
		r.status = RequirementStatus.DRAFT;
		r.version = 1;
		r.statementOrigin = TextOrigin.HUMAN;
		r.verificationOrigin = TextOrigin.HUMAN;
		r.createdBy = createdBy;
		r.createdAt = momento;
		r.updatedAt = momento;
		return r;
	}

	/**
	 * Modifica el texto.
	 *
	 * <p>Un requisito aprobado no admite edicion directa: RQM-08 exige devolverlo
	 * a revision, porque lo que se aprobo fue un texto concreto. La base de datos
	 * impone la misma regla, para que ninguna via la sortee.</p>
	 */
	public void editar(RequirementKind kind, String name, String statement, String verification,
			TextOrigin origenEnunciado, TextOrigin origenCriterio, Instant momento) {

		if (status == RequirementStatus.SUPERSEDED || status == RequirementStatus.ANNULLED) {
			throw new IllegalStateException(
					"Un requisito " + (status == RequirementStatus.SUPERSEDED ? "sustituido" : "anulado")
							+ " no se modifica. Formule una peticion de cambio o de alta a uno nuevo");
		}

		// Modificar devuelve el requisito a su estado inicial: lo revisado o
		// aprobado era otro texto, y darlo por vigente sobre el nuevo seria
		// atribuir a quien decidio algo que no leyo.
		this.status = RequirementStatus.DRAFT;
		this.reviewedBy = null;

		// La clase puede cambiar al modificar. Su identificador de origen lo ajusta
		// el servicio, que es quien conoce los que ya estan tomados en el proyecto.
		if (kind != null) {
			this.kind = kind;
		}

		if (statement != null && !statement.isBlank()) {
			this.statement = TextNormalizer.enunciado(statement);
			this.statementOrigin = origenEnunciado;
		}
		if (verification != null) {
			this.verification = TextNormalizer.enunciado(verification);
			this.verificationOrigin = origenCriterio;
		}
		if (name != null) {
			this.name = TextNormalizer.nombre(name);
		}
		this.version++;
		this.updatedAt = momento;
	}

	/** Deja constancia de quien realiza la revision previa. */
	public void registrarRevision(UUID revisor) {
		this.reviewedBy = revisor;
	}

	public UUID getReviewedBy() {
		return reviewedBy;
	}

	/**
	 * Sustituye el identificador de origen.
	 *
	 * <p>Se emplea al cambiar de clase un requisito: el prefijo de ese
	 * identificador dice si es funcional o no, y conservar el antiguo dejaria un
	 * RF-03 que ya no es funcional. Quien lea la lista creeria lo que dice la
	 * etiqueta, no lo que dice el requisito.</p>
	 */
	/**
	 * Lleva el identificador legible a la version siguiente.
	 *
	 * <p>De REQ-0007-v1 a REQ-0007-v2. Se llama antes de modificar, porque el
	 * numero de version que va en el identificador ha de ser el que tendra el
	 * requisito una vez modificado.</p>
	 */
	public void renumerarVersion() {
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("^(.*)-v(\\d+)$").matcher(readableId);

		if (m.matches()) {
			this.readableId = m.group(1) + "-v" + (Integer.parseInt(m.group(2)) + 1);
		}
	}

	public void renombrarOrigen(String sourceId) {
		this.sourceId = sourceId;
	}

	public void transitarA(RequirementStatus destino, Instant momento) {
		if (!status.puedeTransitarA(destino)) {
			throw new IllegalStateException("Transicion no admitida de " + status + " a " + destino);
		}
		this.status = destino;
		if (destino == RequirementStatus.REVIEWED || destino == RequirementStatus.APPROVED) {
			this.everDecided = true;
		}
		this.updatedAt = momento;
	}

	/**
	 * Indica si el requisito puede eliminarse.
	 *
	 * <p>Solo mientras nada se haya decidido sobre el. Eliminar uno revisado o
	 * aprobado borraria la constancia de esa decision; para eso esta la anulacion,
	 * que lo retira conservando su historia (ADM-01).</p>
	 */
	public boolean puedeEliminarse() {
		return !everDecided;
	}

	public boolean isEverDecided() {
		return everDecided;
	}

	public boolean tieneCriterio() {
		return verification != null && !verification.isBlank();
	}

	/** Indica si le falta algo exigible segun su naturaleza. */
	public boolean incompleto() {
		return statement.isBlank() || (kind.exigeCriterio() && !tieneCriterio());
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

	public String getSourceId() {
		return sourceId;
	}

	public Integer getSourceLine() {
		return sourceLine;
	}

	public RequirementKind getKind() {
		return kind;
	}

	public String getName() {
		return name;
	}

	public String getStatement() {
		return statement;
	}

	/**
	 * Interesado o fuente del requisito. No es el actor de un caso de uso.
	 *
	 * @see org.slcp.service.generation.ActorExtractor para el actor de caso de uso
	 */
	public String getActor() {
		return actor;
	}

	/** Cambia el actor. Vaciarlo lo deja sin declarar, que es un estado legitimo. */
	public void asignarActor(String actor) {
		this.actor = actor == null || actor.isBlank() ? null : actor.trim();
	}

	public String getVerification() {
		return verification;
	}

	public RequirementStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public TextOrigin getStatementOrigin() {
		return statementOrigin;
	}

	public TextOrigin getVerificationOrigin() {
		return verificationOrigin;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Requirement r && Objects.equals(id, r.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
