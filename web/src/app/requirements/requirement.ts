/** Naturaleza de un requisito. */
export type RequirementKind =
  | 'FUNCTIONAL'
  | 'NON_FUNCTIONAL'
  | 'CONSTRAINT'
  | 'USER_STORY'
  | 'USE_CASE'
  | 'OTHER';

/** Hallazgo de la validación, con la característica de la norma que protege. */
export interface Finding {
  rule: string;
  characteristic: string;
  severity: 'DEFECTO' | 'SOSPECHA';
  evidence: string;
  explanation: string;
}

/** Propuesta de criterio de verificación. */
export interface Suggestion {
  text: string;
  rationale: string;
  /** Contiene huecos que una persona debe rellenar: la plataforma no inventa magnitudes. */
  needsDecision: boolean;
}

/** Requisito tal como lo ve quien revisa. */
export interface Requirement {
  readableId: string;
  sourceId: string | null;
  sourceLine: number | null;
  kind: RequirementKind;
  kindLabel: string;
  name: string | null;
  statement: string;
  verification: string | null;
  status: string;
  version: number;
  statementOrigin: 'HUMAN' | 'SUGGESTED';
  verificationOrigin: 'HUMAN' | 'SUGGESTED';
  conforming: boolean;
  findings: Finding[];
  /** Redacciones alternativas del enunciado. */
  statementSuggestions: Suggestion[];
  /** Propuestas de criterio de verificación. */
  suggestions: Suggestion[];
  updatedAt: string;
}

/** Resultado de importar un documento. */
export interface ImportResult {
  found: number;
  imported: number;
  skipped: number;
  skippedIds: string[];
  missingByField: Record<string, number>;
  unknownLabels: string[];
  message: string;
}

/** Resumen del estado de los requisitos. */
export interface RequirementSummary {
  total: number;
  conforming: number;
  withFindings: number;
  withoutCriterion: number;
  approved: number;
  suggestedText: number;
}

export const ETIQUETA_ESTADO: Record<string, string> = {
  DRAFT: 'Borrador',
  REVIEWED: 'Revisado',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
  SUPERSEDED: 'Sustituido',
  ANNULLED: 'Anulado',
};

/**
 * Formato de archivo admitido.
 *
 * El ejemplo llega del servidor y sale del propio perfil, no de una captura:
 * una imagen se desactualiza en cuanto el perfil cambia y nadie se entera.
 */
export interface ImportProfile {
  id: string;
  name: string;
  description: string;
  extensions: string[];
  fields: string[];
  expected: string[];
  example: string;
}
