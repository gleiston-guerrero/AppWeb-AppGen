import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ProjectRole } from '../projects/project';
import { InvitationPreview, InviteResult, JoinResult, PendingInvite } from './invitation';

/** Acceso al flujo de invitaciones, en sus tres vertientes. */
@Injectable({ providedIn: 'root' })
export class InvitationService {
  private readonly http = inject(HttpClient);

  // ---- Quien invita ----

  invitar(projectId: string, email: string, role: ProjectRole): Observable<InviteResult> {
    return this.http.post<InviteResult>(`/api/v1/projects/${projectId}/invitations`, {
      email,
      role,
    });
  }

  vigentes(projectId: string): Observable<PendingInvite[]> {
    return this.http.get<PendingInvite[]>(`/api/v1/projects/${projectId}/invitations`);
  }

  revocar(projectId: string, invitationId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/projects/${projectId}/invitations/${invitationId}`);
  }

  // ---- Quien abre el enlace, sin cuenta todavía ----

  describir(token: string): Observable<InvitationPreview> {
    return this.http.get<InvitationPreview>(`/api/v1/invitations/${token}`);
  }

  completar(
    token: string,
    datos: { username: string; fullName: string; password: string },
  ): Observable<JoinResult> {
    return this.http.post<JoinResult>(`/api/v1/invitations/${token}/completion`, datos);
  }

  // ---- Quien ya tiene cuenta ----

  mias(): Observable<PendingInvite[]> {
    return this.http.get<PendingInvite[]>('/api/v1/my-invitations');
  }

  aceptar(invitationId: string): Observable<JoinResult> {
    return this.http.put<JoinResult>(`/api/v1/my-invitations/${invitationId}/acceptance`, {});
  }

  rechazar(invitationId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/my-invitations/${invitationId}/acceptance`);
  }
}
