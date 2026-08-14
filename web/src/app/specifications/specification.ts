/** Un reparo de la comprobación. */
export interface Issue {
  field: string;
  reason: string;
  severe: boolean;
  source: string;
}

/** Caso de uso expandido o historia de usuario. */
export interface Specification {
  readableId: string;
  kind: 'USE_CASE' | 'USER_STORY';
  kindLabel: string;
  name: string;
  /** Los campos, tal como se guardan. */
  fields: string;
  origin: string;
  originLabel: string;
  status: string;
  /** Aceptada por el equipo: se conserva al regenerar. */
  baseline: boolean;
  /** Algún requisito cambió desde que se aceptó. */
  outdated: boolean;
  version: number;
  acceptedBy: string | null;
  acceptedWithIssues: number;
  requirements: string[];
  issues: Issue[];
  updatedAt: string;
}

export interface SpecificationsState {
  /** Si hay modelo activo. Sin él no puede generarse. */
  assisted: boolean;
  useCases: Specification[];
  userStories: Specification[];
}

/** Plantilla de un caso de uso vacío, para escribirlo desde cero. */
export const CASO_DE_USO_VACIO = {
  nombre: '',
  actorPrincipal: '',
  actoresSecundarios: [],
  objetivo: '',
  precondiciones: [],
  flujoPrincipal: [
    { numero: 1, accionDelActor: 'Este caso de uso inicia cuando…', respuestaDelSistema: '', referencia: '' },
  ],
  flujosAlternativos: [],
  flujosExcepcionales: [],
  postcondicionExito: '',
  postcondicionFracaso: '',
  relaciones: '',
  requisitosEspeciales: '',
  prioridad: '',
  riesgos: '',
};

/** Plantilla de una historia vacía. */
export const HISTORIA_VACIA = {
  descripcion: 'Como …, quiero …, para ….',
  criteriosDeAceptacion: 'Escenario: \n  Dado \n  Cuando \n  Entonces ',
  actor: '',
  funcionalidad: '',
  beneficio: '',
  prioridad: '',
  dependencias: '',
  componentes: '',
  valorDeNegocio: '',
};
