/** Proveedor de IA admitido, con sus valores habituales. */
export interface Provider {
  id: string;
  label: string;
  defaultUrl: string;
  defaultModel: string;
  keysUrl: string;
}

/**
 * Configuración de una función.
 *
 * Nunca incluye la credencial: solo `keyHint`, los cuatro últimos caracteres.
 */
export interface FeatureSettings {
  feature: string;
  featureLabel: string;
  featureDescription: string;
  /** Si la función no puede realizarse sin modelo. */
  essential: boolean;
  /** Qué proveedor sirve a esta función. */
  provider: string;
  providerLabel: string;
  /** Si ese proveedor tiene credencial guardada. */
  hasCredential: boolean;
  enabled: boolean;
  updatedBy: string | null;
  updatedAt: string;
}

/**
 * Una credencial guardada. Nunca incluye la clave.
 *
 * Varias conviven, una por proveedor: es lo que permite compararlos en un ensayo
 * sin perder las demás al cambiar de uno.
 */
export interface Credential {
  provider: string;
  providerLabel: string;
  model: string;
  baseUrl: string;
  keyHint: string;
  updatedBy: string | null;
  updatedAt: string;
}

export interface ProbeResult {
  ok: boolean;
  message: string;
}

/**
 * La instrucción de una función, con la de fábrica al lado.
 *
 * Es de la función y no del proveedor: todas las APIs de esa función reciben la
 * misma, que es lo que hace válida una comparación entre ellas.
 */
export interface Prompt {
  feature: string;
  featureLabel: string;
  template: string;
  defaultTemplate: string;
  /** Si alguien la cambió respecto de la de fábrica. */
  edited: boolean;
  /** Marcas que se sustituyen antes de enviar, con lo que significan. */
  placeholders: Record<string, string>;
  updatedBy: string | null;
  updatedAt: string | null;
}
