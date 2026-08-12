import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../auth/session-service';
import { RecoveryService } from '../recovery/recovery-service';
import { ApiError } from '../registration/registration';

/**
 * Cambio de contraseña con sesión iniciada.
 *
 * Exige la contraseña actual aunque haya sesión: una sesión abierta y
 * desatendida no debe bastar para apropiarse de la cuenta.
 *
 * Al cambiarla se cierran todas las sesiones abiertas, incluida la propia, así
 * que la pantalla lleva de vuelta al acceso.
 */
@Component({
  selector: 'slcp-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './password-page.html',
  styleUrl: './password-page.css',
})
export class PasswordPage {
  private readonly service = inject(RecoveryService);
  private readonly sesion = inject(SessionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly MIN_PASSWORD = 15;

  protected readonly usuario = this.sesion.sesion;
  protected readonly enviando = signal(false);
  protected readonly hecho = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verActual = signal(false);
  protected readonly verNueva = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: [
      '',
      [Validators.required, Validators.minLength(15), Validators.maxLength(200)],
    ],
  });

  protected faltan(): number {
    return Math.max(0, this.MIN_PASSWORD - this.formulario.controls.newPassword.value.length);
  }

  /** Enumera lo pendiente: un botón que no responde y no explica parece averiado. */
  protected faltasPendientes(): string[] {
    const faltas: string[] = [];
    const c = this.formulario.controls;

    if (c.currentPassword.invalid) {
      faltas.push('Escriba su contraseña actual');
    }
    if (c.newPassword.invalid) {
      faltas.push(
        c.newPassword.value.length === 0
          ? `Escriba una contraseña nueva de al menos ${this.MIN_PASSWORD} caracteres`
          : `A la contraseña nueva le faltan ${this.faltan()} caracteres`,
      );
    }
    return faltas;
  }

  protected cambiar(): void {
    this.error.set(null);

    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.error.set('Falta algo antes de continuar: ' + this.faltasPendientes().join('; ') + '.');
      return;
    }

    const { currentPassword, newPassword } = this.formulario.getRawValue();

    if (currentPassword === newPassword) {
      this.error.set('La contraseña nueva es la misma que la actual.');
      return;
    }

    this.enviando.set(true);

    this.service.cambiar(currentPassword, newPassword).subscribe({
      next: () => {
        this.enviando.set(false);
        this.hecho.set(true);
        // La sesión propia también quedó revocada: se descarta para que la
        // interfaz no siga mostrando una sesión que el servidor ya cerró.
        this.sesion.descartar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected volver(): void {
    this.router.navigate(['/entrar']);
  }

  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    if (fallo.status === 401) {
      return 'Su sesión ha caducado. Vuelva a entrar e inténtelo de nuevo.';
    }
    const cuerpo = fallo.error as ApiError | null;
    return cuerpo?.message ?? `El servicio devolvió un error ${fallo.status}.`;
  }
}
