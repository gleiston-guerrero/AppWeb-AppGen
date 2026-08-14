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
 * Credencial de un proveedor en un proyecto.
 *
 * <p>Pertenece al proveedor y no a la funcion: una clave la emite quien la emite,
 * con independencia de para que se use. Guardarlas asi permite que varias
 * convivan --- que es lo que hace falta para compararlas --- y que configurar
 * cinco funciones no obligue a teclear la misma clave cinco veces.</p>
 *
 * <p>La clave se guarda cifrada y esta clase no la descifra: solo la custodia.
 * Quien necesite usarla ha de pedirsela al servicio que tiene la clave maestra,
 * y ese paso extra es a proposito.</p>
 */
@Entity
@Table(name = "ai_credentials")
@IdClass(AiCredential.Clave.class)
public class AiCredential {

	/** Clave compuesta: un proveedor por proyecto. */
	public record Clave(UUID projectId, AiProvider provider) implements Serializable {

		public Clave() {
			this(null, null);
		}
	}

	@Id
	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 30, updatable = false)
	private AiProvider provider;

	@Column(name = "model", nullable = false, length = 120)
	private String model;

	@Column(name = "base_url", nullable = false, length = 400)
	private String baseUrl;

	@Column(name = "api_key_cipher", nullable = false)
	private String apiKeyCipher;

	@Column(name = "key_hint", nullable = false, length = 12)
	private String keyHint;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "lock_version", nullable = false)
	private int lockVersion;

	protected AiCredential() {
	}

	public static AiCredential crear(UUID projectId, AiProvider provider, String model,
			String baseUrl, String apiKeyCipher, String keyHint, UUID autor, Instant momento) {

		AiCredential c = new AiCredential();
		c.projectId = projectId;
		c.provider = provider;
		c.model = model == null || model.isBlank() ? provider.getModeloPorDefecto() : model.trim();
		c.baseUrl = baseUrl == null || baseUrl.isBlank()
				? provider.getDireccionPorDefecto() : baseUrl.trim();
		c.apiKeyCipher = apiKeyCipher;
		c.keyHint = keyHint;
		c.updatedBy = autor;
		c.updatedAt = momento;
		return c;
	}

	/**
	 * Actualiza modelo, direccion y, si llega, la clave.
	 *
	 * <p>La clave solo se toca si viene una nueva: corregir el nombre del modelo no
	 * debe obligar a teclearla otra vez, y quien no la tuviera a mano la borraria
	 * sin querer.</p>
	 */
	public void actualizar(String model, String baseUrl, String apiKeyCipher, String keyHint,
			UUID autor, Instant momento) {

		if (model != null && !model.isBlank()) {
			this.model = model.trim();
		}
		if (baseUrl != null && !baseUrl.isBlank()) {
			this.baseUrl = baseUrl.trim();
		}
		if (apiKeyCipher != null) {
			this.apiKeyCipher = apiKeyCipher;
			this.keyHint = keyHint;
		}

		this.updatedBy = autor;
		this.updatedAt = momento;
	}

	public UUID getProjectId() {
		return projectId;
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

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
