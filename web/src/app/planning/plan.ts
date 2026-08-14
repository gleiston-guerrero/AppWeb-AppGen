/** Horas dedicadas a una actividad. */
export interface TimeEntry {
  /** Necesario para poder retirarlo. */
  id: string;
  person: string;
  hours: number;
  workedOn: string;
  note: string | null;
}

export interface Activity {
  readableId: string;
  name: string;
  plannedEffort: number;
  done: boolean;
  doneAt: string | null;
  spentHours: number;
  entries: TimeEntry[];
}

/** Recurso material asignado a una tarea. */
export interface AssignedResource {
  readableId: string;
  name: string;
  kindLabel: string;
  unit: string | null;
  quantity: number | null;
}

export interface Task {
  readableId: string;
  name: string;
  description: string | null;
  plannedEffort: number;
  assignee: string | null;
  assigneeName: string | null;
  status: 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED';
  statusLabel: string;
  doneBy: string | null;
  /** Calculado de sus actividades. Nadie lo escribe. */
  progress: number;
  spentHours: number;
  activities: Activity[];
  resources: AssignedResource[];
  updatedAt: string;
}

export interface Component {
  readableId: string;
  name: string;
  description: string | null;
  effort: number;
  progress: number;
  spentHours: number;
  deletable: boolean;
  tasks: Task[];
  updatedAt: string;
}

export interface DeliverableBreakdown {
  deliverableId: string;
  deliverableName: string;
  deliverableStatus: string;
  effort: number;
  progress: number;
  spentHours: number;
  components: Component[];
}

export interface Resource {
  readableId: string;
  name: string;
  kind: string;
  kindLabel: string;
  unit: string | null;
  quantity: number | null;
  notes: string | null;
  /** Cuántas tareas lo emplean. Impide borrar lo que está en uso. */
  assignments: number;
}

/** Carga de trabajo de una persona del equipo. */
export interface Workload {
  username: string;
  fullName: string;
  tasks: number;
  effort: number;
  spentHours: number;
  progress: number;
}

export interface Plan {
  effort: number;
  progress: number;
  spentHours: number;
  deliverables: DeliverableBreakdown[];
  resources: Resource[];
  workload: Workload[];
}

/** Clases de recurso material, para el selector. */
export const CLASES_DE_RECURSO = [
  { valor: 'EQUIPMENT', etiqueta: 'Equipo' },
  { valor: 'SOFTWARE', etiqueta: 'Programa o licencia' },
  { valor: 'FACILITY', etiqueta: 'Instalación' },
  { valor: 'CONSUMABLE', etiqueta: 'Consumible' },
  { valor: 'SERVICE', etiqueta: 'Servicio contratado' },
  { valor: 'OTHER', etiqueta: 'Otro' },
] as const;
