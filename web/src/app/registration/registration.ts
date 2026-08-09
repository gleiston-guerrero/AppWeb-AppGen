/** Datos que aporta quien solicita registrarse. Corresponde a RegistrationRequest del servicio. */
export interface RegistrationRequest {
  username: string;
  email: string;
  fullName: string;
  password: string;
}

/** Cuerpo de error uniforme que devuelve el servicio. */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  /** Mensaje por campo cuando el fallo es de validación. */
  fields: Record<string, string>;
}

/** Respuesta del servicio a una solicitud de registro. */
export interface RegistrationResponse {
  readableId: string;
  username: string;
  status: string;
  requestedAt: string;
  message: string;
}
