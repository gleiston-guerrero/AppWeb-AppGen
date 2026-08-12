import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CheckResult,
  HeldRequirement,
  ImportProfile,
  ImportResult,
  Requirement,
  RequirementSummary,
} from './requirement';

/** Acceso a los requisitos de un proyecto. */
@Injectable({ providedIn: 'root' })
export class RequirementService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/requirements`;
  }

  /** Formatos que la plataforma sabe leer, derivados de los perfiles instalados. */
  formatos(): Observable<ImportProfile[]> {
    return this.http.get<ImportProfile[]>('/api/v1/import-profiles');
  }

  listar(projectId: string): Observable<Requirement[]> {
    return this.http.get<Requirement[]>(this.url(projectId));
  }

  resumen(projectId: string): Observable<RequirementSummary> {
    return this.http.get<RequirementSummary>(`${this.url(projectId)}/summary`);
  }

  /** Comprueba un enunciado antes de darlo de alta: duplicados y dominio. */
  comprobar(projectId: string, statement: string): Observable<CheckResult> {
    return this.http.post<CheckResult>(`${this.url(projectId)}/check`, { statement });
  }

  /** Da de alta requisitos que quedaron retenidos, tras decidirlo una persona. */
  aceptarRetenidos(projectId: string, requirements: HeldRequirement[]): Observable<ImportResult> {
    return this.http.post<ImportResult>(`${this.url(projectId)}/held`, { requirements });
  }

  crear(
    projectId: string,
    datos: { sourceId?: string; kind?: string; name?: string; statement: string; verification?: string },
  ): Observable<Requirement> {
    return this.http.post<Requirement>(this.url(projectId), datos);
  }

  /** Envía el documento como texto: la interfaz lo lee en el navegador. */
  importar(projectId: string, profileId: string, content: string): Observable<ImportResult> {
    return this.http.post<ImportResult>(`${this.url(projectId)}/import`, { profileId, content });
  }

  /**
   * Modifica un requisito.
   *
   * `fromSuggestion` marca la procedencia del texto (ANA-16), para que una
   * revisión posterior pueda distinguir qué escribió una persona.
   */
  editar(
    projectId: string,
    readableId: string,
    datos: { name?: string; statement: string; verification?: string },
    fromSuggestion = false,
  ): Observable<Requirement> {
    return this.http.put<Requirement>(
      `${this.url(projectId)}/${readableId}?fromSuggestion=${fromSuggestion}`,
      datos,
    );
  }

  /** Elimina un requisito. Solo si nada se ha decidido sobre él. */
  eliminar(projectId: string, readableId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/${readableId}`);
  }

  transitar(projectId: string, readableId: string, to: string): Observable<Requirement> {
    return this.http.put<Requirement>(
      `${this.url(projectId)}/${readableId}/status?to=${to}`,
      {},
    );
  }
}
