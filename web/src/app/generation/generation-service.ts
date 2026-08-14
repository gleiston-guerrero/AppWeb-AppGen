import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AiSettings, Artifact, GenerationState, ProbeResult, Provider } from './generation';

/** Acceso a la generación de pruebas y diagramas. */
@Injectable({ providedIn: 'root' })
export class GenerationService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/generation`;
  }

  // --- Configuración del servicio de IA ---

  ajustes(projectId: string): Observable<AiSettings> {
    return this.http.get<AiSettings>(`/api/v1/projects/${projectId}/ai-settings`);
  }

  proveedores(projectId: string): Observable<Provider[]> {
    return this.http.get<Provider[]>(`/api/v1/projects/${projectId}/ai-settings/providers`);
  }

  /**
   * Guarda la configuración.
   *
   * La clave solo viaja si se escribió una nueva: enviarla vacía conserva la que
   * hubiera, de modo que corregir el modelo no obliga a tecleárla de nuevo.
   */
  guardarAjustes(
    projectId: string,
    datos: { provider: string; model: string; baseUrl: string; apiKey?: string },
  ): Observable<AiSettings> {
    return this.http.put<AiSettings>(`/api/v1/projects/${projectId}/ai-settings`, datos);
  }

  activarIa(projectId: string, active: boolean): Observable<AiSettings> {
    return this.http.put<AiSettings>(
      `/api/v1/projects/${projectId}/ai-settings/enabled?active=${active}`,
      {},
    );
  }

  retirarCredencial(projectId: string): Observable<AiSettings> {
    return this.http.delete<AiSettings>(`/api/v1/projects/${projectId}/ai-settings/credential`);
  }

  probar(projectId: string): Observable<ProbeResult> {
    return this.http.post<ProbeResult>(`/api/v1/projects/${projectId}/ai-settings/probe`, {});
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
