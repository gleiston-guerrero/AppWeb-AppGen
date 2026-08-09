import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../registration/registration';
import { SessionService } from './session-service';

/**
 * Inicio de sesion.
 *
 * Realiza FUN-03: admite indistintamente el nombre de usuario o el correo. Los
 * mensajes de fallo son los que devuelve el servicio, que distingue entre
 * cuenta inexistente, contrasena incorrecta y cuenta no operativa.
 */
@Component({
  selector: 'slcp-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css',
})
export class LoginPage {
  private readonly sesion = inject(SessionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly bloqueado = signal(false);
  protected readonly verClave = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    identifier: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  protected alternarClave(): void {
    this.verClave.update((v) => !v);
  }

  protected enviar(): void {
    this.error.set(null);
    this.bloqueado.set(false);

    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);

    this.sesion.entrar(this.formulario.getRawValue()).subscribe({
      next: (s) => {
        this.enviando.set(false);
        this.router.navigate([s.platformRole === 'ADMINISTRATOR' ? '/administracion' : '/trabajo']);
      },
      error: (fallo: HttpErrorResponse) => {
        this.enviando.set(false);

        if (fallo.status === 0) {
          this.error.set('El servicio no responde. Compruebe que está en marcha en el puerto 8081.');
          return;
        }

        const cuerpo = fallo.error as ApiError | null;
        this.bloqueado.set(fallo.status === 429);
        this.error.set(cuerpo?.message ?? 'No se pudo iniciar sesión.');
      },
    });
  }
}
