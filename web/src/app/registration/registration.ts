/** Datos que aporta quien solicita registrarse. Corresponde a RegistrationRequest del servicio. */
export interface RegistrationRequest {
  username: string;
  email: string;
  fullName: string;
}

/** Respuesta del servicio a una solicitud de registro. */
export interface RegistrationResponse {
  readableId: string;
  username: string;
  status: string;
  requestedAt: string;
  message: string;
}
