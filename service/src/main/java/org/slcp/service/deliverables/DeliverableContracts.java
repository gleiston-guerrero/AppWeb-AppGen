package org.slcp.service.deliverables;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Contratos de los entregables. */
public final class DeliverableContracts {

	private DeliverableContracts() {
	}

	/** Alta o modificacion. */
	public record DeliverableRequest(
			@NotBlank(message = "El nombre del entregable es obligatorio")
			@Size(max = 300) String name,

			@Size(max = 4000) String description,

			@Size(max = 4000) String acceptance,

			/** Requisitos aprobados que este entregable realiza (WBS-07). */
			List<String> requirementIds) {
	}

	/** Requisito enlazado, tal como se muestra junto al entregable. */
	public record LinkedRequirement(String readableId, String statement, boolean closed) {
	}

	/** Entregable tal como lo ve quien consulta. */
	public record DeliverableView(
			String readableId,
			String name,
			String description,
			String acceptance,
			String status,
			String statusLabel,
			int version,
			boolean deletable,
			String acceptedBy,
			Instant acceptedAt,
			List<LinkedRequirement> requirements,
			Instant updatedAt) {
	}

	/** Requisito aprobado del proyecto, para poder enlazarlo. */
	public record LinkableRequirement(
			String readableId, String name, String statement, boolean alreadyLinked) {
	}
}
