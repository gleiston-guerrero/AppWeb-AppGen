import { ProjectRole } from '../projects/project';

/** Camino que sigue una incorporación, según a quién se incorpora. */
export type Camino =
  | 'INCORPORADA_DIRECTAMENTE'
  | 'PENDIENTE_DE_ACEPTACION'
  | 'PENDIENTE_DE_REGISTRO';

/** Resultado de invitar. El enlace solo llega mientras no haya envío de correo. */
export interface InviteResult {
  camino: Camino;
  email: string;
  role: ProjectRole;
  roleLabel: string;
  expiresAt: string;
  /**
   * Enlace de un solo uso. Llega **solo si la entrega por correo falló**: si el
   * correo salió, quien invita no debe verlo, porque entonces podría usarlo él
   * mismo y crear una cuenta a nombre de una dirección ajena.
   */
  link: string | null;
  message: string;
}

/** Invitación vigente. */
export interface PendingInvite {
  id: string;
  email: string;
  role: ProjectRole;
  roleLabel: string;
  createdAt: string;
  expiresAt: string;
  tieneCuenta: boolean;
}

/** Lo que ve quien abre un enlace, antes de decidir. */
export interface InvitationPreview {
  valid: boolean;
  reason: string;
  projectName: string;
  projectReadableId: string;
  invitedBy: string;
  email: string;
  role: ProjectRole;
  roleLabel: string;
  roleScope: string;
  requiereRegistro: boolean;
}

/** Resultado de completar o aceptar. */
export interface JoinResult {
  projectReadableId: string;
  projectName: string;
  role: ProjectRole;
  roleLabel: string;
  message: string;
}
