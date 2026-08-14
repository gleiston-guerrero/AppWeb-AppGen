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
  /** Quién ejerce lo que el requisito describe. Nulo si no se declaró. */
  actor: string | null;
  status: string;
  version: number;
  /** Quién realizó la revisión previa. Nulo si aún no se revisó. */
  reviewedBy: string | null;
  statementOrigin: 'HUMAN' | 'SUGGESTED';
  verificationOrigin: 'HUMAN' | 'SUGGESTED';
  conforming: boolean;
  /** Si nada se ha decidido sobre él y por tanto puede eliminarse. */
  deletable: boolean;
  findings: Finding[];
  /** Redacciones alternativas del enunciado. */
  statementSuggestions: Suggestion[];
  /** Propuestas de criterio de verificación. */
  suggestions: Suggestion[];
  updatedAt: string;
}

/** Resultado de importar un documento. */
/** Requisito omitido por decir lo mismo que otro ya presente. */
export interface Duplicate {
  sourceId: string | null;
  matchedReadableId: string;
  matchedSourceId: string | null;
  similarity: number;
  matchedStatement: string;
}

/** Requisito cuyo identificador de origen estaba tomado por otro distinto. */
export interface Renumbered {
  from: string;
  to: string;
  statement: string;
}

/** Requisito que se parece a otro sin llegar a ser el mismo. */
export interface Suspected {
  readableId: string;
  sourceId: string | null;
  similarToReadableId: string;
  similarity: number;
  similarStatement: string;
}

/** Aviso de que lo examinado no parece del mismo asunto que el proyecto. */
export interface DomainAlert {
  alert: boolean;
  overlap: number;
  sharedTerms: string[];
  newTerms: string[];
  message: string;
}

/** Resultado de la comprobación previa al alta manual. */
export interface CheckResult {
  similar: Suspected[];
  domain: DomainAlert;
  crossCutting: HeldGroup[];
  foreign: HeldGroup[];
  /** Campos importados que conviene revisar antes de fiarse de ellos. */
  fieldIssues: FieldIssue[];
  clean: boolean;
}

/** Requisito leído pero no dado de alta, a la espera de decisión. */
export interface HeldRequirement {
  sourceId: string | null;
  kind: string;
  name: string | null;
  statement: string;
  verification: string | null;
  actor: string | null;
}

/**
 * Requisito retenido por parecerse a uno ya presente.
 *
 * Viajan los dos enunciados: la pregunta es si dicen lo mismo, y eso no puede
 * responderse viendo uno solo.
 */
export interface HeldSuspect {
  requirement: HeldRequirement;
  matchedReadableId: string;
  matchedSourceId: string | null;
  similarity: number;
  matchedStatement: string;
}

/** Conjunto de requisitos retenidos que tratan de lo mismo. */
export interface HeldGroup {
  label: string;
  terms: string[];
  requirements: HeldRequirement[];
}

/**
 * Campo que llegó con un valor que no puede darse por bueno.
 *
 * No impide la importación: señala que hay que mirarlo antes de que algo dependa
 * de él.
 */
export interface FieldIssue {
  requirement: string;
  field: string;
  value: string;
  reason: string;
  severe: boolean;
}

export interface ImportResult {
  found: number;
  imported: number;
  skipped: number;
  duplicates: Duplicate[];
  renumbered: Renumbered[];
  suspected: HeldSuspect[];
  domain: DomainAlert;
  /** Exigencias que valen para cualquier sistema: las decide una persona. */
  crossCutting: HeldGroup[];
  /** Requisitos que parecen de otro asunto: no se dan de alta sin decisión. */
  foreign: HeldGroup[];
  /** Campos importados que conviene revisar antes de fiarse de ellos. */
  fieldIssues: FieldIssue[];
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
