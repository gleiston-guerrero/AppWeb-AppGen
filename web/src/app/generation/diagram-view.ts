import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

/**
 * Dibuja un diagrama de Mermaid dentro de la aplicación.
 *
 * La biblioteca se carga bajo demanda, no al arrancar: pesa bastante y solo hace
 * falta en esta pantalla. Quien nunca abra los diagramas no la descarga.
 *
 * Si el diagrama no puede dibujarse se muestra el error junto al texto que lo
 * produjo, en lugar de dejar un hueco en blanco: un diagrama generado puede
 * traer una construcción que Mermaid no admita, y quien lo vea necesita saber
 * qué línea corregir.
 */
@Component({
  selector: 'slcp-diagram-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as e) {
      <div class="diagrama__error">
        <p><strong>No se pudo dibujar este diagrama.</strong> {{ e }}</p>
        <p class="diagrama__pista">
          Corrija el texto con <em>Modificar</em>: lo que se guarda es el texto, y de él sale
          el dibujo.
        </p>
      </div>
    }

    <div class="diagrama" [class.diagrama--vacio]="!dibujado()" #destino></div>

    @if (!dibujado() && !error()) {
      <p class="diagrama__cargando">Dibujando…</p>
    }
  `,
  styles: `
    .diagrama {
      background: var(--surface);
      border: 1px solid var(--rule);
      padding: 1rem;
      overflow-x: auto;
      text-align: center;
    }
    .diagrama--vacio { min-height: 3rem; }
    .diagrama svg { max-width: 100%; height: auto; }

    .diagrama__error {
      background: #fdf2f1;
      border-left: 3px solid #a3261f;
      padding: 0.7rem 1rem;
      margin-bottom: 0.5rem;
      font-size: var(--step--1);
    }
    .diagrama__pista { color: var(--ink-soft); margin-top: 0.3rem; }
    .diagrama__cargando { font-size: var(--step--1); color: var(--ink-faint); }
  `,
})
export class DiagramView {
  /** Texto del diagrama en Mermaid. */
  readonly codigo = input.required<string>();

  /** Identificador único: Mermaid exige uno distinto por dibujo. */
  readonly id = input.required<string>();

  private readonly destino = viewChild.required<ElementRef<HTMLDivElement>>('destino');

  protected readonly dibujado = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Se carga una sola vez para toda la aplicación. */
  private static biblioteca: Promise<typeof import('mermaid')> | null = null;

  constructor() {
    effect(() => {
      const texto = this.codigo();
      const identificador = this.id();
      void this.dibujar(texto, identificador);
    });
  }

  private async dibujar(texto: string, identificador: string): Promise<void> {
    this.dibujado.set(false);
    this.error.set(null);

    try {
      DiagramView.biblioteca ??= import('mermaid');
      const mermaid = (await DiagramView.biblioteca).default;

      mermaid.initialize({
        startOnLoad: false,
        theme: 'neutral',
        // La plataforma escapa lo que va en las cajas, de modo que no hace falta
        // que Mermaid interprete etiquetas: dejarlo estricto evita que un texto
        // de requisito acabe ejecutándose como marcado.
        securityLevel: 'strict',
        fontFamily: 'inherit',
      });

      const { svg } = await mermaid.render('m-' + identificador + '-' + Date.now(), texto);

      this.destino().nativeElement.innerHTML = svg;
      this.dibujado.set(true);
    } catch (fallo) {
      // Mermaid deja restos del intento fallido en el documento.
      document.querySelectorAll('[id^="dm-"]').forEach((n) => n.remove());

      this.destino().nativeElement.innerHTML = '';
      this.error.set(fallo instanceof Error ? fallo.message : 'Error al interpretar el texto.');
    }
  }
}
