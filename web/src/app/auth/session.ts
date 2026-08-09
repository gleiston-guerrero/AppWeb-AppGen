/** Datos de la sesion iniciada. No contiene token alguno: viajan en cookies. */
export interface Session {
  userId: string;
  readableId: string;
  username: string;
  fullName: string;
  /**
   * Rol global: qué puede hacerse SIN proyecto. Los roles dentro de cada
   * proyecto son otra cosa y viajan con cada proyecto.
   */
  platformRole: 'MEMBER' | 'FACILITATOR' | 'ADMINISTRATOR';
  mustChangePassword: boolean;
  expiresAt: string;
}

/** Credenciales de acceso. */
export interface Credentials {
  identifier: string;
  password: string;
}
