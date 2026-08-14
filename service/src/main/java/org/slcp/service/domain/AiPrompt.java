package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Instruccion propia de un proyecto para una funcion.
 *
 * <p>Si no existe fila, rige la de fabrica del catalogo. Volver a la de fabrica
 * consiste en borrar esta, no en copiarla: asi la de fabrica sigue mejorando con
 * las versiones sin que nadie arrastre una copia vieja.</p>
 *
 * <p>Es de la funcion y no del proveedor: todas las APIs de una funcion reciben
 * la misma, que es lo que hace valida una comparacion entre ellas.</p>
 */
@Entity
@Table(name = "ai_prompts")
@IdClass(AiPrompt.Clave.class)
public class AiPrompt {

	public record Clave(UUID projectId, AiFeature feature) implements Serializable {

		public Clave() {
			this(null, null);
		}
	}

	@Id
	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "feature", nullable = false, length = 40, updatable = false)
	private AiFeature feature;

	@Column(name = "template", nullable = false)
	private String template;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected AiPrompt() {
	}

	public static AiPrompt crear(UUID projectId, AiFeature feature, String template, UUID autor,
			Instant momento) {

		AiPrompt p = new AiPrompt();
		p.projectId = projectId;
		p.feature = feature;
		p.template = template;
		p.updatedBy = autor;
		p.updatedAt = momento;
		return p;
	}

	public void actualizar(String template, UUID autor, Instant momento) {
		this.template = template;
		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public AiFeature getFeature() {
		return feature;
	}

	public String getTemplate() {
		return template;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
