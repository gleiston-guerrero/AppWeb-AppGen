import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Specification, SpecificationsState } from './specification';

/** Acceso a los casos de uso e historias. */
@Injectable({ providedIn: 'root' })
export class SpecificationService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/specifications`;
  }

  estado(projectId: string): Observable<SpecificationsState> {
    return this.http.get<SpecificationsState>(this.url(projectId));
  }

  /** Genera con el modelo configurado. Sin él, el servicio lo rechaza y explica. */
  generar(
    projectId: string,
    datos: { kind: string; requirements: string[] },
  ): Observable<Specification[]> {
    return this.http.post<Specification[]>(`${this.url(projectId)}/generate`, datos);
  }

  /** Alta escrita desde cero, sin modelo. */
  crear(
    projectId: string,
    datos: { kind: string; name: string; fields: string; requirements: string[] },
  ): Observable<Specification> {
    return this.http.post<Specification>(this.url(projectId), datos);
  }

  editar(
    projectId: string,
    specId: string,
    datos: { kind: string; name: string; fields: string; requirements: string[] },
  ): Observable<Specification> {
    return this.http.put<Specification>(`${this.url(projectId)}/${specId}`, datos);
  }

  aceptar(projectId: string, specId: string): Observable<Specification> {
    return this.http.put<Specification>(`${this.url(projectId)}/${specId}/acceptance`, {});
  }

  descartar(projectId: string, specId: string): Observable<Specification> {
    return this.http.put<Specification>(`${this.url(projectId)}/${specId}/discard`, {});
  }

  /** Retira la regla base para poder regenerar. */
  retirarReglaBase(projectId: string, specId: string): Observable<Specification> {
    return this.http.delete<Specification>(`${this.url(projectId)}/${specId}/baseline`);
  }

  eliminar(projectId: string, specId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/${specId}`);
  }
}
