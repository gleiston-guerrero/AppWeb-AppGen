import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Respuesta a la petición de recuperación. Nunca contiene el enlace. */
export interface ResetResponse {
  sent: boolean;
  message: string;
}

/** Estado de un enlace de recuperación. */
export interface ResetPreview {
  valid: boolean;
  reason: string;
  username: string;
}

/** Recuperación y cambio de contraseña. */
@Injectable({ providedIn: 'root' })
export class RecoveryService {
  private readonly http = inject(HttpClient);

  static readonly URL = '/api/v1/password-resets';

  solicitar(identifier: string): Observable<ResetResponse> {
    return this.http.post<ResetResponse>(RecoveryService.URL, { identifier });
  }

  describir(token: string): Observable<ResetPreview> {
    return this.http.get<ResetPreview>(`${RecoveryService.URL}/${token}`);
  }

  restablecer(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${RecoveryService.URL}/${token}/password`, { password });
  }

  /** Cambio con sesión iniciada. Exige la contraseña actual. */
  cambiar(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>('/api/v1/auth/sessions/current/password', {
      currentPassword,
      newPassword,
    });
  }
}
