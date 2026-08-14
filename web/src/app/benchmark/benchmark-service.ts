import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { BenchmarkRun } from './benchmark';

/** Acceso a los ensayos comparativos. */
@Injectable({ providedIn: 'root' })
export class BenchmarkService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/benchmarks`;
  }

  historial(projectId: string): Observable<BenchmarkRun[]> {
    return this.http.get<BenchmarkRun[]>(this.url(projectId));
  }

  ejecutar(
    projectId: string,
    datos: {
      feature: string;
      subkind: string;
      requirements: string[];
      providers: string[];
      notes: string;
    },
  ): Observable<BenchmarkRun> {
    return this.http.post<BenchmarkRun>(this.url(projectId), datos);
  }
}
