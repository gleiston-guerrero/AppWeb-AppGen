import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Member, Project, ProjectRole } from './project';

/** Acceso a proyectos y equipo. */
@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);

  static readonly URL = '/api/v1/projects';

  /** Proyectos donde quien pregunta tiene membresía activa. */
  mios(): Observable<Project[]> {
    return this.http.get<Project[]>(ProjectService.URL);
  }

  crear(name: string, purpose: string): Observable<Project> {
    return this.http.post<Project>(ProjectService.URL, { name, purpose });
  }

  editar(readableId: string, name: string, purpose: string): Observable<Project> {
    return this.http.put<Project>(`${ProjectService.URL}/${readableId}`, { name, purpose });
  }

  /** Retira el proyecto del servicio o lo devuelve a él. El contenido permanece. */
  cambiarEstado(readableId: string, active: boolean): Observable<Project> {
    return this.http.put<Project>(
      `${ProjectService.URL}/${readableId}/status?active=${active}`,
      {},
    );
  }

  /** Elimina el proyecto. Solo mientras esté vacío. */
  eliminar(readableId: string): Observable<void> {
    return this.http.delete<void>(`${ProjectService.URL}/${readableId}`);
  }

  equipo(readableId: string): Observable<Member[]> {
    return this.http.get<Member[]>(`${ProjectService.URL}/${readableId}/team`);
  }

  incorporar(readableId: string, identifier: string, role: ProjectRole): Observable<Member> {
    return this.http.post<Member>(`${ProjectService.URL}/${readableId}/team`, {
      identifier,
      role,
    });
  }
}
