import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RegistrationRequest, RegistrationResponse } from './registration';

/** Envio de solicitudes de registro. Realiza FUN-15 en la interfaz. */
@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);

  static readonly URL = '/api/v1/registrations';

  solicitar(peticion: RegistrationRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(RegistrationService.URL, peticion);
  }
}
