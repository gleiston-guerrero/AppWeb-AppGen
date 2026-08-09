/** Solicitud de registro a la espera de decision. */
export interface PendingRegistration {
  readableId: string;
  username: string;
  email: string;
  fullName: string;
  requestedAt: string;
}

/** Decision del administrador. El motivo es obligatorio al rechazar (ROL-05). */
export interface ApprovalDecision {
  approved: boolean;
  reason?: string;
}

/** Resultado de la decision. */
export interface ApprovalResult {
  readableId: string;
  username: string;
  status: string;
  decidedAt: string;
  message: string;
}
