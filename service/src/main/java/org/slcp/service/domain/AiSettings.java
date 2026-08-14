package org.slcp.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Configuracion del servicio de IA de un proyecto.
 *
 * <p>La credencial se guarda cifrada y esta clase no la descifra: solo la
 * custodia. Quien necesite usarla ha de pedirla expresamente al servicio que
 * tiene la clave maestra, y ese paso extra es a proposito.</p>
 */
@Entity
@Table(name = "ai_settings")
@IdClass(AiSettings.Clave.class)
public class AiSettings {

	/** Clave compuesta: cada funcion de cada proyecto tiene su configuracion. */
	public record Clave(UUID projectId, AiFeature feature) implements java.io.Serializable {

		public Clave() {
			this(null, null);
		}
	}

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "feature", nullable = false, length = 40, updatable = false)
	private AiFeature feature;

	@Id
	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 30)
	private AiProvider provider;

	@Column(name = "model", nullable = false, length = 120)
	private String model;

	@Column(name = "base_url", nullable = false, length = 400)
	private String baseUrl;

	@Column(name = "api_key_cipher")
	private String apiKeyCipher;

	@Column(name = "key_hint", length = 12)
	private String keyHint;

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
		s.model = AiProvider.ANTHROPIC.getModeloPorDefecto();
		s.baseUrl = AiProvider.ANTHROPIC.getDireccionPorDefecto();
		s.enabled = false;
		s.updatedBy = autor;
		s.updatedAt = momento;
		return s;
	}

	/**
	 * Cambia proveedor, modelo y direccion.
	 *
	 * <p>Cambiar de proveedor retira la credencial: una clave de un servicio no
	 * vale en otro, y conservarla haria que la plataforma la enviase a un tercero
	 * distinto del que la emitio.</p>
	 */
	public void configurar(AiProvider proveedor, String modelo, String direccion, Instant momento,
			UUID autor) {

		boolean cambiaDeProveedor = proveedor != null && proveedor != this.provider;

		if (cambiaDeProveedor) {
			this.provider = proveedor;
			this.apiKeyCipher = null;
			this.keyHint = null;
			this.enabled = false;
		}

		// Al cambiar de proveedor se toman sus valores habituales aunque no lleguen:
		// conservar los del anterior dejaria el modelo de un servicio apuntando a
		// otro, y el fallo aparecería al generar, lejos de donde se decidió.
		if (modelo != null && !modelo.isBlank()) {
			this.model = modelo.trim();
		} else if (cambiaDeProveedor || this.model == null || this.model.isBlank()) {
			this.model = this.provider.getModeloPorDefecto();
		}

		if (direccion != null && !direccion.isBlank()) {
			this.baseUrl = direccion.trim();
		} else if (cambiaDeProveedor || this.baseUrl == null || this.baseUrl.isBlank()) {
			this.baseUrl = this.provider.getDireccionPorDefecto();
		}

		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	/** Guarda la credencial ya cifrada, junto con su pista. */
	public void guardarCredencial(String cifrada, String pista, Instant momento, UUID autor) {
		this.apiKeyCipher = cifrada;
		this.keyHint = pista;
		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	/** Retira la credencial y desactiva el servicio: sin clave no puede operar. */
	public void retirarCredencial(Instant momento, UUID autor) {
		this.apiKeyCipher = null;
		this.keyHint = null;
		this.enabled = false;
		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	public void activar(boolean activo, Instant momento, UUID autor) {
		if (activo && apiKeyCipher == null) {
			throw new IllegalStateException(
					"No puede activarse sin credencial: quedaria activo de nombre y fallaria en "
							+ "cada generacion, y ese fallo se leeria como que el modelo no sirve");
		}
		this.enabled = activo;
		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	public boolean tieneCredencial() {
		return apiKeyCipher != null;
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

	public String getModel() {
		return model;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	/** Solo lo usa quien tiene la clave maestra para descifrarla. */
	public String getApiKeyCipher() {
		return apiKeyCipher;
	}

	public String getKeyHint() {
		return keyHint;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}
}
