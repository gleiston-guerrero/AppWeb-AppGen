/** Prueba o diagrama generado. */
export interface Artifact {
  readableId: string;
  kind: 'TEST' | 'DIAGRAM';
  subkind: string;
  subkindLabel: string;
  title: string;
  content: string;
  format: string;
  origin: 'DERIVED' | 'ASSISTED' | 'HUMAN';
  originLabel: string;
  rationale: string | null;
  /** Si contiene huecos que una persona debe rellenar. */
  needsDecision: boolean;
  status: 'PROPOSED' | 'ACCEPTED' | 'DISCARDED';
  version: number;
  reviewedBy: string | null;
  /** Se aceptó teniendo huecos: la decisión del equipo prevaleció. */
  acceptedWithGaps: boolean;
  /** Propietario que lo dio por revisado. No es una aprobación. */
  ownerReviewedBy: string | null;
  ownerReviewedAt: string | null;
  requirements: string[];
  updatedAt: string;
}

/** Cobertura de pruebas de un requisito aprobado. */
export interface Coverage {
  readableId: string;
  sourceId: string | null;
  name: string | null;
  tests: number;
  acceptedTests: number;
  /** Cubierto solo si tiene alguna prueba aceptada. */
  covered: boolean;
}

export interface GenerationState {
  testKinds: string[];
  diagramKinds: string[];
  /** Si hay un modelo conectado o se generan derivadas. */
  assisted: boolean;
  tests: Artifact[];
  diagrams: Artifact[];
  coverage: Coverage[];
}

export const ETIQUETA_CLASE: Record<string, string> = {
  ACCEPTANCE: 'Aceptación',
  BOUNDARY: 'Límites',
  NEGATIVE: 'Camino negativo',
  PERFORMANCE: 'Rendimiento',
  USE_CASE: 'Casos de uso',
  STATE: 'Estados',
  CONTEXT: 'Contexto',
  TRACEABILITY: 'Trazabilidad',
};

export const EXPLICACION_CLASE: Record<string, string> = {
  ACCEPTANCE: 'El camino que el requisito describe, traducido de su criterio de verificación.',
  BOUNDARY: 'Comportamiento en los límites de las magnitudes que el requisito declara.',
  NEGATIVE: 'Qué ocurre cuando no se cumple la condición. Casi ningún requisito lo dice.',
  PERFORMANCE: 'Medida de la magnitud exigida, solo para requisitos no funcionales.',
  USE_CASE: 'Quién actúa sobre qué, agrupado por el actor que declara cada requisito.',
  STATE: 'Estados y transiciones de los requisitos que activan o cierran algo.',
  CONTEXT: 'El sistema y los actores que lo rodean, sin entrar en el detalle.',
  TRACEABILITY: 'Mapa de los requisitos por familia, distinguiendo los que tienen criterio.',
};

/** Proveedor de IA admitido, con sus valores habituales. */
export interface Provider {
  id: string;
  label: string;
  defaultUrl: string;
  defaultModel: string;
  keysUrl: string;
}

/**
 * Configuración del servicio de IA de un proyecto.
 *
 * Nunca incluye la credencial: solo `keyHint`, los cuatro últimos caracteres,
 * que bastan para reconocerla y no sirven para usarla.
 */
export interface AiSettings {
  provider: string;
  providerLabel: string;
  model: string;
  baseUrl: string;
  keyHint: string | null;
  hasKey: boolean;
  enabled: boolean;
  updatedBy: string | null;
  updatedAt: string;
}

export interface ProbeResult {
  ok: boolean;
  message: string;
}
