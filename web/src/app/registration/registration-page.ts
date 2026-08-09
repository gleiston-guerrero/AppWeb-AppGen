import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiError, RegistrationResponse } from './registration';
import { RegistrationService } from './registration-service';

/**
 * Solicitud de registro como facilitador de proyectos.
 *
 * Realiza FUN-15 y FUN-05 en la interfaz. No ofrece elección de rol: el único
 * obtenible por autorregistro es el de facilitador, y permitir elegirlo sería
 * una vía de elección de privilegio.
 */
@Component({
  selector: 'slcp-registration-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './registration-page.html',
  styleUrl: './registration-page.css',
})
export class RegistrationPage {
  private readonly service = inject(RegistrationService);
  private readonly fb = inject(FormBuilder);

  /** Longitud mínima exigida por FUN-05, alineada con NIST SP 800-63B-4. */
  static readonly MIN_PASSWORD = 15;

  protected readonly enviando = signal(false);
  protected readonly resultado = signal<RegistrationResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly verClave = signal(false);

  /**
   * Mensajes por campo que devuelve el servicio.
   *
   * La validación del navegador y la del servicio no tienen por qué coincidir:
   * la segunda conoce cosas que la primera no puede saber, como que un nombre
   * de usuario ya esté en uso. Descartar sus mensajes obligaría a la persona a
   * adivinar qué corregir.
   */
  protected readonly erroresServidor = signal<Record<string, string>>({});

  protected readonly formulario = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(160)]],
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(60),
        Validators.pattern(/^[a-zA-Z0-9._-]+$/),
      ],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(RegistrationPage.MIN_PASSWORD),
        Validators.maxLength(200),
      ],
    ],
  });

  protected get minPassword(): number {
    return RegistrationPage.MIN_PASSWORD;
  }

  /** Caracteres que faltan para alcanzar el mínimo. Cero si ya se alcanzó. */
  protected faltan(): number {
    return Math.max(0, RegistrationPage.MIN_PASSWORD - this.formulario.controls.password.value.length);
  }

  protected alternarClave(): void {
    this.verClave.update((v) => !v);
  }

  protected enviar(): void {
    this.erroresServidor.set({});
    this.error.set(null);

    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);

    this.service.solicitar(this.formulario.getRawValue()).subscribe({
      next: (respuesta) => {
        this.resultado.set(respuesta);
        this.enviando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.interpretar(fallo);
        this.enviando.set(false);
      },
    });
  }

  /** Traduce el fallo a una indicación de qué hacer, no a una disculpa. */
  private interpretar(fallo: HttpErrorResponse): void {
    if (fallo.status === 0) {
      this.error.set('El servicio no responde. Compruebe que está en marcha en el puerto 8081.');
      return;
    }

    const cuerpo = fallo.error as ApiError | null;

    if (cuerpo?.fields && Object.keys(cuerpo.fields).length > 0) {
      this.erroresServidor.set(cuerpo.fields);
      this.error.set(null);
      return;
    }

    this.error.set(cuerpo?.message ?? 'El servicio devolvió un error ' + fallo.status + '.');
  }
}
