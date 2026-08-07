import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PlatformInfo } from './platform-info';

/**
 * Acceso a la informacion publica de la plataforma.
 *
 * La ruta es relativa a proposito: el servidor de desarrollo la redirige al
 * servicio mediante proxy.conf.json, de modo que el navegador no realiza
 * peticiones entre origenes distintos y no hace falta configurar CORS.
 */
@Injectable({ providedIn: 'root' })
export class PlatformInfoService {
  private readonly http = inject(HttpClient);

  /** Ruta del recurso, en kebab-case y plural conforme a NAM. */
  static readonly URL = '/api/v1/platform-info';

  load(): Observable<PlatformInfo> {
    return this.http.get<PlatformInfo>(PlatformInfoService.URL);
  }
}
