/**
 * Requisito enlazado a un entregable.
 *
 * Trae los dos identificadores: el del documento de origen —RF-01, RNF-02—, que
 * es el que la gente del proyecto reconoce, y el de la plataforma, que no cambia
 * aunque el documento se renumere.
 */
export interface LinkedRequirement {
  readableId: string;
  sourceId: string | null;
  kind: string;
  kindLabel: string;
  name: string | null;
  statement: string;
  /** Calculado: todos sus entregables aceptados (RQM-14). */
  closed: boolean;
}

/** Entregable de un proyecto. */
export interface Deliverable {
  readableId: string;
  name: string;
  description: string | null;
  acceptance: string | null;
  status: 'PLANNED' | 'IN_PROGRESS' | 'DELIVERED' | 'ACCEPTED' | 'REJECTED';
  statusLabel: string;
  version: number;
  deletable: boolean;
  acceptedBy: string | null;
  acceptedAt: string | null;
  requirements: LinkedRequirement[];
  updatedAt: string;
}

/** Requisito aprobado que puede enlazarse. */
export interface LinkableRequirement {
  readableId: string;
  sourceId: string | null;
  kind: string;
  kindLabel: string;
  name: string | null;
  statement: string;
  alreadyLinked: boolean;
}
