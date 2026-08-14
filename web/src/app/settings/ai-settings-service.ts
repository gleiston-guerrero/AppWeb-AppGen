import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FeatureSettings, ProbeResult, Provider } from './ai-settings';

/** Acceso a la configuración del servicio de IA, por función. */
@Injectable({ providedIn: 'root' })
export class AiSettingsService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/ai-settings`;
  }

  /** Todas las funciones, incluidas las que nadie ha configurado. */
  funciones(projectId: string): Observable<FeatureSettings[]> {
    return this.http.get<FeatureSettings[]>(this.url(projectId));
  }

  proveedores(projectId: string): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.url(projectId)}/providers`);
  }

  /**
   * Guarda la configuración de una función.
   *
   * La clave solo viaja si se escribió una nueva: enviarla vacía conserva la que
   * hubiera, de modo que corregir el modelo no obliga a teclearla otra vez.
   */
  guardar(
    projectId: string,
    feature: string,
    datos: { provider: string; model: string; baseUrl: string; apiKey?: string },
  ): Observable<FeatureSettings> {
    return this.http.put<FeatureSettings>(`${this.url(projectId)}/${feature}`, datos);
  }

  activar(projectId: string, feature: string, active: boolean): Observable<FeatureSettings> {
    return this.http.put<FeatureSettings>(
      `${this.url(projectId)}/${feature}/enabled?active=${active}`,
      {},
    );
  }

  retirarCredencial(projectId: string, feature: string): Observable<FeatureSettings> {
    return this.http.delete<FeatureSettings>(`${this.url(projectId)}/${feature}/credential`);
  }

  probar(projectId: string, feature: string): Observable<ProbeResult> {
    return this.http.post<ProbeResult>(`${this.url(projectId)}/${feature}/probe`, {});
  }
}
