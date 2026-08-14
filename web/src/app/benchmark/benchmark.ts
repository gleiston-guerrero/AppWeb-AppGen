/** Lo que un proveedor produjo, con sus medidas. */
export interface BenchmarkResult {
  provider: string;
  providerLabel: string;
  model: string;
  produced: number;
  /** Sin huecos: listas para ejecutar tal cual. */
  complete: number;
  /** Cifras que inventó y la salvaguarda sustituyó. Menos es mejor. */
  invented: number;
  issues: number;
  elapsedMs: number;
  failed: boolean;
  failureReason: string | null;
  sample: string | null;
}

export interface BenchmarkRun {
  id: string;
  feature: string;
  featureLabel: string;
  subkind: string | null;
  requirements: string[];
  runBy: string | null;
  runAt: string;
  notes: string | null;
  results: BenchmarkResult[];
}

/** Funciones que pueden ensayarse hoy. */
export const FUNCIONES_ENSAYABLES = [
  { id: 'GENERATE_TESTS', etiqueta: 'Generar pruebas' },
  { id: 'GENERATE_SPECS', etiqueta: 'Generar casos de uso e historias' },
] as const;

export const CLASES_POR_FUNCION: Record<string, { id: string; etiqueta: string }[]> = {
  GENERATE_TESTS: [
    { id: 'ACCEPTANCE', etiqueta: 'Aceptación' },
    { id: 'BOUNDARY', etiqueta: 'Límites' },
    { id: 'NEGATIVE', etiqueta: 'Camino negativo' },
    { id: 'PERFORMANCE', etiqueta: 'Rendimiento' },
  ],
  GENERATE_SPECS: [
    { id: 'USE_CASE', etiqueta: 'Caso de uso expandido' },
    { id: 'USER_STORY', etiqueta: 'Historia de usuario' },
  ],
};
