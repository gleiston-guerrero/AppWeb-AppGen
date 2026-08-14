import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RequirementReport } from './report';

/** Acceso al informe de requisitos. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  informe(projectId: string): Observable<RequirementReport> {
    return this.http.get<RequirementReport>(
      `/api/v1/projects/${projectId}/requirements/report`,
    );
  }

  /**
   * Pide el archivo al servicio.
   *
   * Se genera allí y no aquí porque hay una restricción que cumplir: un archivo
   * armado con datos que ya están en la pantalla no restringe nada.
   */
  exportar(projectId: string): Observable<Blob> {
    return this.http.get(`/api/v1/projects/${projectId}/requirements/report/export`, {
      responseType: 'blob',
    });
  }
}
