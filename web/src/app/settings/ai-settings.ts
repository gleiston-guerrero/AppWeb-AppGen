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
