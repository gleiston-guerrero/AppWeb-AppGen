import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiError } from '../registration/registration';
import { InvitationPreview, JoinResult } from './invitation';
import { InvitationService } from './invitation-service';

/**
 * Pantalla de quien abre un enlace de invitación.
 *
 * Es pública: quien llega aquí todavía no tiene cuenta con la que autenticarse.
 * La autorización la aporta el propio enlace, que es aleatorio, de un solo uso
 * y está ligado a un correo y a un proyecto.
 *
 * Realiza INV-02 e INV-03: el rol no aparece como campo editable en ninguna
 * parte, y hasta que alguien consume el enlace no se revela nada del contenido
 * del proyecto.
 */
@Component({
  selector: 'slcp-invitation-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './invitation-page.html',
  styleUrl: './invitation-page.css',
})
export class InvitationPage implements OnInit {
  private readonly service = inject(InvitationService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly MIN_PASSWORD = 15;

  protected readonly cargando = signal(true);
  protected readonly invitacion = signal<InvitationPreview | null>(null);
  protected readonly resultado = signal<JoinResult | null>(null);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verClave = signal(false);

  private token = '';

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
    password: ['', [Validators.required, Validators.minLength(15), Validators.maxLength(200)]],
  });

  ngOnInit(): void {
    this.token = this.ruta.snapshot.paramMap.get('token') ?? '';
    this.service.describir(this.token).subscribe({
      next: (p) => {
        this.invitacion.set(p);
        this.cargando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  protected faltan(): number {
    return Math.max(0, this.MIN_PASSWORD - this.formulario.controls.password.value.length);
  }

  protected alternarClave(): void {
    this.verClave.update((v) => !v);
  }

  /**
   * Enumera lo que falta, campo por campo.
   *
   * Un formulario que se niega a enviarse sin decir por qué es indistinguible de
   * uno averiado. El botón permanece habilitado a propósito: deshabilitarlo
   * ocultaría igualmente el motivo, y quien mira no tendría a qué agarrarse.
   */
  protected faltasPendientes(): string[] {
    const faltas: string[] = [];
    const c = this.formulario.controls;

    if (c.fullName.invalid) {
      faltas.push('Escriba su nombre completo');
    }
    if (c.username.invalid) {
      faltas.push(
        c.username.value.length === 0
          ? 'Elija un nombre de usuario'
          : 'El nombre de usuario admite entre 3 y 60 caracteres, sin espacios',
      );
    }
    if (c.password.invalid) {
      faltas.push(
        c.password.value.length === 0
          ? `Escriba una contraseña de al menos ${this.MIN_PASSWORD} caracteres`
          : `A la contraseña le faltan ${this.faltan()} caracteres`,
      );
    }
    return faltas;
  }

  protected completar(): void {
    this.error.set(null);

    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.error.set('Falta algo antes de continuar: ' + this.faltasPendientes().join('; ') + '.');
      return;
    }
    this.enviando.set(true);
    this.error.set(null);

    this.service.completar(this.token, this.formulario.getRawValue()).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.enviando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.enviando.set(false);
      },
    });
  }

  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    const cuerpo = fallo.error as ApiError | null;
    return cuerpo?.message ?? `El servicio devolvió un error ${fallo.status}.`;
  }
}
