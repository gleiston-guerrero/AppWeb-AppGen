/** Una decisión tomada sobre una versión del requisito. */
export interface DecisionLine {
  version: number;
  decision: string;
  actor: string;
  at: string;
  statement: string;
}

/** Un requisito con todo lo que consta de él. */
export interface ReportRow {
  readableId: string;
  sourceId: string | null;
  kind: string;
  kindLabel: string;
  name: string | null;
  statement: string;
  /** El criterio de verificación. Nulo si no lo tiene. */
  verification: string | null;
  /** Interesado o fuente. No es el actor de un caso de uso. */
  actor: string | null;
  status: string;
  version: number;
  statementOrigin: string | null;
  verificationOrigin: string | null;
  conforming: boolean;
  findings: string[];
  tests: number;
  acceptedTests: number;
  covered: boolean;
  decisions: DecisionLine[];
  updatedAt: string;
}

/** Lo que el conjunto revela y una ficha suelta no. */
export interface ReportSummary {
  total: number;
  approved: number;
  withoutCriterion: number;
  withFindings: number;
  withoutTests: number;
  suggestedText: number;
  withoutActor: number;
}

export interface RequirementReport {
  projectId: string;
  projectName: string;
  generatedAt: string;
  /**
   * Si quien consulta puede llevarse el informe.
   *
   * El equipo y el facilitador sí; el propietario del producto no. Sirve para no
   * ofrecer un botón que fallaría: la restricción se impone en el servidor.
   */
  canExport: boolean;
  summary: ReportSummary;
  rows: ReportRow[];
}
