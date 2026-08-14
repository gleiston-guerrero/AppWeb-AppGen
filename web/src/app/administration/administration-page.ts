import { HttpErrorResponse } from '@angular/common/http';

import { conservarPosicion } from '../shared/desplazamiento';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../auth/session-service';
import { ApiError } from '../registration/registration';
import { PendingRegistration } from './administration';
import { AdministrationService } from './administration-service';

/**
 * Pantalla del administrador: solicitudes a la espera de decision.
 *
 * Realiza FUN-16 y ROL-05. El rechazo exige motivo, y la pantalla no permite
 * enviarlo sin el, del mismo modo que el servicio lo rechaza.
 */
@Component({
  selector: 'slcp-administration-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './administration-page.html',
  styleUrl: './administration-page.css',
})
export class AdministrationPage implements OnInit {
  private readonly service = inject(AdministrationService);
  private readonly sesion = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly pendientes = signal<PendingRegistration[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  /** Identificador de la solicitud cuyo rechazo se está redactando. */
  protected readonly rechazando = signal<string | null>(null);
  protected motivo = '';
  protected readonly enviando = signal<string | null>(null);

  protected readonly usuario = this.sesion.sesion;

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);
    this.service.pendientes().subscribe({
      next: (lista) => {
        this.pendientes.set(lista);
        this.cargando.set(false);
        volver();
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  protected aprobar(solicitud: PendingRegistration): void {
    this.decidir(solicitud, true);
  }

  protected abrirRechazo(solicitud: PendingRegistration): void {
    this.motivo = '';
    this.rechazando.set(solicitud.readableId);
  }

  protected cancelarRechazo(): void {
    this.rechazando.set(null);
    this.motivo = '';
  }

  protected confirmarRechazo(solicitud: PendingRegistration): void {
    if (this.motivo.trim().length === 0) {
      return;
    }
    this.decidir(solicitud, false, this.motivo.trim());
  }

  protected salir(): void {
    this.sesion.salir().subscribe({
      next: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/']),
    });
  }

  private decidir(solicitud: PendingRegistration, aprobada: boolean, motivo?: string): void {
    this.enviando.set(solicitud.readableId);
    this.error.set(null);

    this.service.decidir(solicitud.readableId, { approved: aprobada, reason: motivo }).subscribe({
      next: (resultado) => {
        this.enviando.set(null);
        this.rechazando.set(null);
        this.motivo = '';
        this.aviso.set(resultado.username + ': ' + resultado.message);
        this.pendientes.update((lista) =>
          lista.filter((s) => s.readableId !== solicitud.readableId),
        );
      },
      error: (fallo: HttpErrorResponse) => {
        this.enviando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    if (fallo.status === 403) {
      return 'Su cuenta no tiene atribuciones de administración.';
    }
    const cuerpo = fallo.error as ApiError | null;
    return cuerpo?.message ?? 'El servicio devolvió un error ' + fallo.status + '.';
  }
}
