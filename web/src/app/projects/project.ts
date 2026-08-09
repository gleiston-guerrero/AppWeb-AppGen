/** Rol dentro de un proyecto. Se resuelve por proyecto, nunca por cuenta (ROL-01). */
export type ProjectRole = 'PROJECT_FACILITATOR' | 'TEAM_MEMBER' | 'PRODUCT_OWNER';

/** Proyecto tal como lo ve quien consulta, con sus propios roles en él. */
export interface Project {
  readableId: string;
  name: string;
  purpose: string;
  status: string;
  createdAt: string;
  myRoles: ProjectRole[];
  teamSize: number;
}

/** Integrante del equipo. */
export interface Member {
  username: string;
  fullName: string;
  email: string;
  role: ProjectRole;
  roleLabel: string;
  status: string;
}

/** Etiquetas en castellano de cada rol. */
export const ETIQUETA_ROL: Record<ProjectRole, string> = {
  PROJECT_FACILITATOR: 'Facilitador',
  TEAM_MEMBER: 'Miembro del equipo',
  PRODUCT_OWNER: 'Propietario del producto',
};

/** Qué puede hacer cada rol, para explicarlo en la interfaz. */
export const ALCANCE_ROL: Record<ProjectRole, string> = {
  PROJECT_FACILITATOR: 'Organiza el proyecto, planifica e incorpora al equipo.',
  TEAM_MEMBER: 'Trabaja los requisitos, genera y modifica artefactos.',
  PRODUCT_OWNER: 'Verifica y aprueba. No modifica nada.',
};
