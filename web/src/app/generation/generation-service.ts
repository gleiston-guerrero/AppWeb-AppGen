import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Artifact, GenerationState } from './generation';

/** Acceso a la generación de pruebas y diagramas. */
@Injectable({ providedIn: 'root' })
export class GenerationService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/generation`;
  }

  estado(projectId: string): Observable<GenerationState> {
    return this.http.get<GenerationState>(this.url(projectId));
  }

  /** Genera pruebas o diagramas. En cualquier orden: no dependen entre sí. */
  generar(
    projectId: string,
    datos: {
      kind: 'TEST' | 'DIAGRAM';
      subkinds: string[];
      requirements: string[];
      /** Lo elige quien genera, no la plataforma. */
      mode: 'DERIVED' | 'ASSISTED';
    },
  ): Observable<Artifact[]> {
    return this.http.post<Artifact[]>(this.url(projectId), datos);
  }

  editar(
    projectId: string,
    artifactId: string,
    datos: { title?: string; content: string },
  ): Observable<Artifact> {
    return this.http.put<Artifact>(`${this.url(projectId)}/${artifactId}`, datos);
  }

  decidir(projectId: string, artifactId: string, accept: boolean): Observable<Artifact> {
    return this.http.put<Artifact>(
      `${this.url(projectId)}/${artifactId}/decision?accept=${accept}`,
      {},
    );
  }

  /** El propietario del producto lo da por revisado. No es aprobarlo. */
  darPorRevisado(projectId: string, artifactId: string, reviewed: boolean): Observable<Artifact> {
    return this.http.put<Artifact>(
      `${this.url(projectId)}/${artifactId}/owner-review?reviewed=${reviewed}`,
      {},
    );
  }

  eliminar(projectId: string, artifactId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/${artifactId}`);
  }
}
