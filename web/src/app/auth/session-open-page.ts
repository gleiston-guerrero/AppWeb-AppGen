import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from './session-service';

/**
 * Aviso de que ya hay una sesión abierta.
 *
 * Se muestra en lugar de la pantalla de acceso. No se cierra la sesión sola:
 * quien llegó aquí pudo hacerlo por error, y cerrarle la sesión sin preguntar le
 * costaría el trabajo que tuviera a medias en otra pestaña.
 */
@Component({
  selector: 'slcp-session-open-page',
  imports: [RouterLink],
  template: `
    <main class="pagina contenedor">
      <section>
        <p class="eyebrow">Sesión abierta</p>
        <h1 class="titulo">Ya hay alguien dentro</h1>

        @if (usuario(); as u) {
          <p class="entrada">
            Esta aplicación está abierta como <span class="mono">{{ u.username }}</span>. Para
            entrar con otra cuenta hay que cerrar antes esta sesión.
          </p>
        }

        <p class="entrada">
          No se cierra sola a propósito: si llegó aquí por error, podría tener trabajo a medias en
          otra pestaña.
        </p>

        <div class="acciones">
          <button type="button" class="boton" [disabled]="saliendo()" (click)="salir()">
            {{ saliendo() ? 'Cerrando…' : 'Cerrar sesión y entrar con otra cuenta' }}
          </button>
          <a class="boton boton--plano" routerLink="/trabajo">Seguir con esta sesión</a>
        </div>
      </section>
    </main>
  `,
  styles: `
    .pagina { padding-block: var(--gap-l); }
    .titulo { font-size: var(--step-3); margin-bottom: var(--gap-s); }
    .entrada { max-width: 62ch; margin-bottom: var(--gap-s); }
    .acciones { display: flex; gap: 0.6rem; flex-wrap: wrap; margin-top: var(--gap-s); }
    .boton--plano { background: transparent; color: var(--ink); border: 1px solid var(--rule); }
  `,
})
export class SessionOpenPage {
  private readonly sesion = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly usuario = this.sesion.sesion;
  protected readonly saliendo = signal(false);

  protected salir(): void {
    this.saliendo.set(true);
    this.sesion.salir().subscribe({
      next: () => {
        this.saliendo.set(false);
        this.router.navigate(['/entrar']);
      },
      // Aunque el servidor falle, la sesión local se descarta: dejarla puesta
      // sería peor que perderla.
      error: () => {
        this.saliendo.set(false);
        this.sesion.descartar();
        this.router.navigate(['/entrar']);
      },
    });
  }
}
