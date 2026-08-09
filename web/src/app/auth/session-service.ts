import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { Credentials, Session } from './session';

/**
 * Estado de la sesion en la interfaz.
 *
 * Los tokens no aparecen por aqui en ningun momento: viajan en cookies que este
 * codigo no puede leer, conforme a SEC-01. Lo que se guarda es solo quien esta
 * atendiendose, para decidir que mostrar.
 *
 * Que la interfaz oculte una opcion no autoriza nada: SEC-05 exige que toda
 * restriccion visible este impuesta tambien en el servicio.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  static readonly URL = '/api/v1/auth/sessions';

  private readonly estado = signal<Session | null>(null);
  private readonly comprobada = signal(false);

  readonly sesion = this.estado.asReadonly();
  readonly resuelta = this.comprobada.asReadonly();
  readonly autenticado = computed(() => this.estado() !== null);
  readonly esAdministrador = computed(() => this.estado()?.platformRole === 'ADMINISTRATOR');

  /** Inicia sesion. El servidor responde con las cookies. */
  entrar(credenciales: Credentials): Observable<Session> {
    return this.http
      .post<Session>(SessionService.URL, credenciales)
      .pipe(tap((s) => this.aplicar(s)));
  }

  /** Recupera la sesion vigente, si la cookie sigue siendo valida. */
  recuperar(): Observable<Session> {
    return this.http
      .get<Session>(SessionService.URL + '/current')
      .pipe(tap((s) => this.aplicar(s)));
  }

  /** Cierra la sesion. La revocacion la hace el servidor, no basta olvidarla aqui. */
  salir(): Observable<void> {
    return this.http.delete<void>(SessionService.URL + '/current').pipe(
      tap(() => {
        this.estado.set(null);
        this.comprobada.set(true);
      }),
    );
  }

  /** Marca la sesion como inexistente tras una comprobacion fallida. */
  descartar(): void {
    this.estado.set(null);
    this.comprobada.set(true);
  }

  private aplicar(s: Session): void {
    this.estado.set(s);
    this.comprobada.set(true);
  }
}
