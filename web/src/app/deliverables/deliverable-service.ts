import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Deliverable, LinkableRequirement } from './deliverable';

/** Acceso a los entregables de un proyecto. */
@Injectable({ providedIn: 'root' })
export class DeliverableService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/deliverables`;
  }

  listar(projectId: string): Observable<Deliverable[]> {
    return this.http.get<Deliverable[]>(this.url(projectId));
  }

  /** Requisitos aprobados que pueden enlazarse. */
  enlazables(projectId: string, deliverable?: string): Observable<LinkableRequirement[]> {
    const sufijo = deliverable ? `?deliverable=${deliverable}` : '';
    return this.http.get<LinkableRequirement[]>(
      `${this.url(projectId)}/linkable-requirements${sufijo}`,
    );
  }

  crear(
    projectId: string,
    datos: { name: string; description?: string; acceptance?: string; requirementIds: string[] },
  ): Observable<Deliverable> {
    return this.http.post<Deliverable>(this.url(projectId), datos);
  }

  editar(
    projectId: string,
    readableId: string,
    datos: { name: string; description?: string; acceptance?: string; requirementIds: string[] },
  ): Observable<Deliverable> {
    return this.http.put<Deliverable>(`${this.url(projectId)}/${readableId}`, datos);
  }

  eliminar(projectId: string, readableId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/${readableId}`);
  }

  transitar(projectId: string, readableId: string, to: string): Observable<Deliverable> {
    return this.http.put<Deliverable>(
      `${this.url(projectId)}/${readableId}/status?to=${to}`,
      {},
    );
  }
}
