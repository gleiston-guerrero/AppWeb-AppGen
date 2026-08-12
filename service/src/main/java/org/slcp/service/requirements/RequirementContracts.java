package org.slcp.service.requirements;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contratos de los requisitos, agrupados por pertenecer al mismo flujo. */
public final class RequirementContracts {

	private RequirementContracts() {
	}

	/** Alta o edicion de un requisito escrito a mano. */
	public record RequirementRequest(
			@Size(max = 40) String sourceId,
			@Size(max = 20) String kind,
			@Size(max = 300) String name,

			@NotBlank(message = "El enunciado es obligatorio")
			@Size(max = 4000) String statement,

			@Size(max = 4000) String verification) {
	}

	/** Carga de un documento completo. */
	public record ImportRequest(
			@NotBlank(message = "Indique el perfil de importacion") String profileId,
			@NotBlank(message = "El documento esta vacio") String content) {
	}

	/** Hallazgo de la validacion, tal como llega a la interfaz. */
	public record FindingView(
			String rule,
			String characteristic,
			String severity,
			String evidence,
			String explanation) {
	}

	/** Propuesta de criterio. */
	public record SuggestionView(String text, String rationale, boolean needsDecision) {
	}

	/**
	 * Requisito tal como lo ve quien revisa.
	 *
	 * <p>Los hallazgos y las sugerencias no se almacenan: se calculan al
	 * consultar. Guardarlos los dejaria obsoletos en cuanto cambiaran las reglas,
	 * y Q-65 exige que un cambio de reglas se refleje sin alterar estados.</p>
	 */
	public record RequirementView(
			String readableId,
			String sourceId,
			Integer sourceLine,
			String kind,
			String kindLabel,
			String name,
			String statement,
			String verification,
			String status,
			int version,
			/** Quien realizo la revision previa, para impedir que apruebe lo que reviso. */
			String reviewedBy,
			String statementOrigin,
			String verificationOrigin,
			boolean conforming,
			/** Si nada se ha decidido sobre el y por tanto puede eliminarse. */
			boolean deletable,
			List<FindingView> findings,
			/** Propuestas de redaccion del enunciado (ANA-14). */
			List<SuggestionView> statementSuggestions,
			/** Propuestas de criterio de verificacion. */
			List<SuggestionView> suggestions,
			Instant updatedAt) {
	}

	/** Resultado de importar un documento. */
	public record ImportResult(
			int found,
			int imported,
			int skipped,
			List<String> skippedIds,
			Map<String, Integer> missingByField,
			List<String> unknownLabels,
			String message) {
	}

	/** Resumen del estado de los requisitos de un proyecto. */
	public record RequirementSummary(
			long total,
			long conforming,
			long withFindings,
			long withoutCriterion,
			long approved,
			long suggestedText) {
	}
}
