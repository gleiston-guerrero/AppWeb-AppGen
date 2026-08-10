import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiError } from '../registration/registration';
import { RecoveryService, ResetPreview } from './recovery-service';

/**
 * Recuperación de acceso, en sus dos momentos.
 *
 * Sin token en la dirección, pide el identificador y envía el correo. Con token,
 * comprueba el enlace y permite fijar la contraseña nueva.
 *
 * El enlace no se muestra nunca aquí: quien pide una recuperación es anónimo, y
 * devolvérselo entregaría cualquier cuenta a quien la pidiera.
 */
@Component({
  selector: 'slcp-recovery-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './recovery-page.html',
  styleUrl: './recovery-page.css',
})
export class RecoveryPage implements OnInit {
  private readonly service = inject(RecoveryService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly MIN_PASSWORD = 15;

  protected token = '';
  protected readonly comprobando = signal(false);
  protected readonly enlace = signal<ResetPreview | null>(null);
  protected readonly enviado = signal<string | null>(null);
  protected readonly hecho = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verClave = signal(false);

  protected readonly solicitud = this.fb.nonNullable.group({
    identifier: ['', [Validators.required]],
  });

  protected readonly cambio = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(15), Validators.maxLength(200)]],
  });

  ngOnInit(): void {
    this.token = this.ruta.snapshot.paramMap.get('token') ?? '';
    if (!this.token) {
      return;
    }
    this.comprobando.set(true);
    this.service.describir(this.token).subscribe({
      next: (p) => {
        this.enlace.set(p);
        this.comprobando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.comprobando.set(false);
      },
    });
  }

  protected faltan(): number {
    return Math.max(0, this.MIN_PASSWORD - this.cambio.controls.password.value.length);
  }

  protected alternarClave(): void {
    this.verClave.update((v) => !v);
  }

  protected solicitar(): void {
    this.error.set(null);
    if (this.solicitud.invalid) {
      this.solicitud.markAllAsTouched();
      this.error.set('Indique su nombre de usuario o su correo.');
      return;
    }
    this.enviando.set(true);

    this.service.solicitar(this.solicitud.getRawValue().identifier).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.enviado.set(r.message);
      },
      error: (fallo: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected restablecer(): void {
    this.error.set(null);
    if (this.cambio.invalid) {
      this.cambio.markAllAsTouched();
      this.error.set(
        `La contraseña debe tener al menos ${this.MIN_PASSWORD} caracteres; le faltan ${this.faltan()}.`,
      );
      return;
    }
    this.enviando.set(true);

    this.service.restablecer(this.token, this.cambio.getRawValue().password).subscribe({
      next: () => {
        this.enviando.set(false);
        this.hecho.set(true);
      },
      error: (fallo: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(this.explicar(fallo));
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
