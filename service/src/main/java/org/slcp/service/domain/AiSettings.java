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
 * Que proveedor sirve a cada funcion de un proyecto.
 *
 * <p>Aqui no vive ninguna credencial: las claves pertenecen al proveedor, se
 * guardan en {@link AiCredential}, y varias pueden convivir. Eso es lo que
 * permite comparar cuatro proveedores sin perder la clave de los otros tres cada
 * vez que se cambia de uno.</p>
 *
 * <p>Esta entidad solo dice a cual se llama en cada funcion, y si la asistencia
 * esta activa.</p>
 */
@Entity
@Table(name = "ai_settings")
@IdClass(AiSettings.Clave.class)
public class AiSettings {

	/** Clave compuesta: cada funcion de cada proyecto elige su proveedor. */
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

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 30)
	private AiProvider provider;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected AiSettings() {
	}

	public static AiSettings inicial(UUID projectId, AiFeature feature, UUID autor,
			Instant momento) {

		AiSettings s = new AiSettings();
		s.projectId = projectId;
		s.feature = feature;
		s.provider = AiProvider.ANTHROPIC;
		s.enabled = false;
		s.updatedBy = autor;
		s.updatedAt = momento;
		return s;
	}

	/**
	 * Elige que proveedor sirve a esta funcion.
	 *
	 * <p>No retira ninguna credencial: viven aparte y varias conviven. Cambiar de
	 * proveedor aqui solo cambia a cual se llama, y la clave del anterior sigue
	 * guardada por si se vuelve a el.</p>
	 */
	public void configurar(AiProvider proveedor, Instant momento, UUID autor) {
		if (proveedor != null) {
			this.provider = proveedor;
		}
		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	/**
	 * Activa o desactiva la asistencia en esta funcion.
	 *
	 * <p>Que exista credencial del proveedor lo comprueba la base de datos: aqui no
	 * puede saberse, porque las credenciales viven en otra tabla.</p>
	 */
	public void activar(boolean activo, Instant momento, UUID autor) {
		this.enabled = activo;
		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public AiFeature getFeature() {
		return feature;
	}

	public AiProvider getProvider() {
		return provider;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
