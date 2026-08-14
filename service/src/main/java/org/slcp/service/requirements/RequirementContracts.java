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

			@Size(max = 4000) String verification,

			/** Quien ejerce lo que el requisito describe. Puede no declararse. */
			@Size(max = 200) String actor) {
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
			/** Quien ejerce lo que el requisito describe. Nulo si no se declaro. */
			String actor,
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
	/** Requisito omitido por decir lo mismo que otro ya presente. */
	public record DuplicateView(
			String sourceId,
			String matchedReadableId,
			String matchedSourceId,
			double similarity,
			String matchedStatement) {
	}

	/** Requisito cuyo identificador de origen estaba tomado por otro distinto. */
	public record RenumberedView(String from, String to, String statement) {
	}

	/** Requisito que se parece a uno ya presente sin llegar a ser el mismo. */
	public record SuspectedView(
			String readableId,
			String sourceId,
			String similarToReadableId,
			double similarity,
			String similarStatement) {
	}

	/** Aviso de que lo examinado no parece del mismo asunto que el proyecto. */
	public record DomainAlert(
			boolean alert,
			double overlap,
			List<String> sharedTerms,
			List<String> newTerms,
			String message) {
	}

	/** Peticion de comprobacion previa al alta manual. */
	public record CheckRequest(String statement) {
	}

	/**
	 * Resultado de la comprobacion previa.
	 *
	 * <p>Se comprueba antes de crear y no despues porque quien escribe puede
	 * querer corregir; avisar una vez creado obligaria a deshacer.</p>
	 */
	public record CheckResult(
			List<SuspectedView> similar,
			DomainAlert domain,
			boolean clean) {
	}

	/**
	 * Requisito retenido: leido pero no dado de alta.
	 *
	 * <p>Viaja entero de vuelta para que pueda darse de alta despues sin volver a
	 * subir el documento. Guardarlo en la base mientras se decide seria darle un
	 * estado mas que mantener, y un requisito a medias entre existir y no existir
	 * es peor que no tenerlo.</p>
	 */
	public record HeldRequirement(
			String sourceId,
			String kind,
			String name,
			String statement,
			String verification,
			String actor) {
	}

	/**
	 * Requisito retenido por parecerse a uno ya presente.
	 *
	 * <p>Viajan los dos enunciados, el que llega y aquel al que se parece: la
	 * pregunta que hay que responder es si dicen lo mismo, y esa no puede
	 * responderse viendo uno solo.</p>
	 */
	public record HeldSuspect(
			HeldRequirement requirement,
			String matchedReadableId,
			String matchedSourceId,
			double similarity,
			String matchedStatement) {
	}

	/**
	 * Reparo sobre un campo que llego en el documento.
	 *
	 * <p>No impide la importacion: senala que ese valor no puede darse por bueno
	 * sin mirarlo. Rechazar el requisito entero por un campo dudoso perderia un
	 * enunciado que quiza esta bien.</p>
	 */
	public record FieldIssue(
			String requirement, String field, String value, String reason, boolean severe) {
	}

	/** Conjunto de requisitos retenidos que tratan de lo mismo. */
	public record HeldGroup(
			String label,
			List<String> terms,
			List<HeldRequirement> requirements) {
	}

	/** Peticion de alta de requisitos retenidos, tras decidirlo una persona. */
	public record AcceptHeldRequest(List<HeldRequirement> requirements) {
	}

	public record ImportResult(
			int found,
			int imported,
			int skipped,
			/** Los omitidos, con el requisito al que se parecen y cuanto. */
			List<DuplicateView> duplicates,
			/** Los que entraron con otro identificador por estar el suyo tomado. */
			List<RenumberedView> renumbered,
			/** Retenidos por parecerse a uno ya presente: los decide una persona. */
			List<HeldSuspect> suspected,
			/** Aviso si el documento no parece del mismo asunto que el proyecto. */
			DomainAlert domain,
			/** Exigencias que valen para cualquier sistema: las decide una persona. */
			List<HeldGroup> crossCutting,
			/** Requisitos que parecen de otro asunto: no se dan de alta sin decision. */
			List<HeldGroup> foreign,
			/** Campos que llegaron con un valor que no puede darse por bueno. */
			List<FieldIssue> fieldIssues,
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
