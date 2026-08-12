/** Requisito enlazado a un entregable. */
export interface LinkedRequirement {
  readableId: string;
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
  name: string | null;
  statement: string;
  alreadyLinked: boolean;
}
