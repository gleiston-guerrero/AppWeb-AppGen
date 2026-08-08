import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { RegistrationResponse } from './registration';
import { RegistrationService } from './registration-service';

/**
 * Solicitud de registro como facilitador de proyectos.
 *
 * Realiza FUN-15 en la interfaz. No ofrece eleccion de rol: el unico obtenible
 * por autorregistro es el de facilitador, y permitir elegirlo seria una via de
 * eleccion de privilegio.
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

  protected readonly enviando = signal(false);
  protected readonly resultado = signal<RegistrationResponse | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(160)]],
    username: [
      '',
      [Validators.required, Validators.minLength(3), Validators.maxLength(60), Validators.pattern(/^[a-zA-Z0-9._-]+$/)],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.service.solicitar(this.formulario.getRawValue()).subscribe({
      next: (respuesta) => {
        this.resultado.set(respuesta);
        this.enviando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.enviando.set(false);
      },
    });
  }

  /** Traduce el fallo a una indicacion de que hacer, no a una disculpa. */
  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    if (fallo.status === 409) {
      return 'Ese nombre de usuario o ese correo ya están registrados. Use otros.';
    }
    if (fallo.status === 400) {
      return 'Alguno de los datos no es válido. Revise el formulario.';
    }
    return 'El servicio devolvió un error ' + fallo.status + '. Vuelva a intentarlo.';
  }
}
