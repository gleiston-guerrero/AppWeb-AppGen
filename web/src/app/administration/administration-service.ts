import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApprovalDecision, ApprovalResult, PendingRegistration } from './administration';

/** Acceso a las funciones de administracion de la plataforma. */
@Injectable({ providedIn: 'root' })
export class AdministrationService {
  private readonly http = inject(HttpClient);

  static readonly URL = '/api/v1/administration';

  pendientes(): Observable<PendingRegistration[]> {
    return this.http.get<PendingRegistration[]>(AdministrationService.URL + '/registrations/pending');
  }

  /** La aprobacion es un recurso de la solicitud y se sustituye con PUT (API-01). */
  decidir(readableId: string, decision: ApprovalDecision): Observable<ApprovalResult> {
    return this.http.put<ApprovalResult>(
      AdministrationService.URL + '/registrations/' + readableId + '/approval',
      decision,
    );
  }
}
