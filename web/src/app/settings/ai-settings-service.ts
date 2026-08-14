import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Credential, FeatureSettings, ProbeResult, Prompt, Provider } from './ai-settings';

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

  /** Las instrucciones de todas las funciones, editadas o de fábrica. */
  prompts(projectId: string): Observable<Prompt[]> {
    return this.http.get<Prompt[]>(`${this.url(projectId)}/prompts`);
  }

  /** Guarda la instrucción de una función. Rige para todas sus APIs. */
  guardarPrompt(projectId: string, feature: string, template: string): Observable<Prompt> {
    return this.http.put<Prompt>(`${this.url(projectId)}/prompts/${feature}`, { template });
  }

  /** Vuelve a la instrucción de fábrica. */
  restaurarPrompt(projectId: string, feature: string): Observable<Prompt> {
    return this.http.delete<Prompt>(`${this.url(projectId)}/prompts/${feature}`);
  }

  /** Credenciales guardadas. Varias conviven, una por proveedor. */
  credenciales(projectId: string): Observable<Credential[]> {
    return this.http.get<Credential[]>(`${this.url(projectId)}/credentials`);
  }

  /** Guarda o cambia la credencial de un proveedor. Una vez, para todas las funciones. */
  guardarCredencial(
    projectId: string,
    provider: string,
    datos: { model: string; baseUrl: string; apiKey?: string },
  ): Observable<Credential> {
    return this.http.put<Credential>(`${this.url(projectId)}/credentials/${provider}`, datos);
  }

  retirarCredencial(projectId: string, provider: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/credentials/${provider}`);
  }

  probar(projectId: string, provider: string): Observable<ProbeResult> {
    return this.http.post<ProbeResult>(
      `${this.url(projectId)}/credentials/${provider}/probe`,
      {},
    );
  }

  /**
   * Elige qué proveedor sirve a una función.
   *
   * La clave solo viaja si se escribió una nueva: enviarla vacía conserva la que
   * hubiera, de modo que corregir el modelo no obliga a teclearla otra vez.
   */
  elegirProveedor(
    projectId: string,
    feature: string,
    provider: string,
  ): Observable<FeatureSettings> {
    return this.http.put<FeatureSettings>(`${this.url(projectId)}/${feature}`, { provider });
  }

  activar(projectId: string, feature: string, active: boolean): Observable<FeatureSettings> {
    return this.http.put<FeatureSettings>(
      `${this.url(projectId)}/${feature}/enabled?active=${active}`,
      {},
    );
  }


}
