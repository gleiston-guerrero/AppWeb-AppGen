import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PlatformInfo } from '../platform-info/platform-info';
import { PlatformInfoService } from '../platform-info/platform-info-service';

/**
 * Pagina principal, dirigida al visitante sin sesion iniciada.
 *
 * Realiza FUN-01 y FUN-02. El encabezado no afirma lo que hace la plataforma:
 * lo muestra, con un requisito real y los artefactos que de el se derivan.
 */
@Component({
  selector: 'slcp-home-page',
  imports: [RouterLink],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage implements OnInit {
  private readonly service = inject(PlatformInfoService);

  protected readonly info = signal<PlatformInfo | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal(false);

  /**
   * Ejemplo mostrado en el encabezado. Es contenido de la propia plataforma y
   * no un adorno: el enunciado sigue los patrones EARS que adopta SPC-01, y los
   * identificadores respetan el esquema de NAM.
   */
  protected readonly ejemplo = {
    requisito: {
      id: 'REQ-MAT-0017-v1',
      patron: 'EARS · dirigido por evento',
      texto:
        'Cuando el estudiante confirma la matrícula, el sistema debe registrar la inscripción y emitir el comprobante.',
    },
    derivados: [
      { id: 'SCN-MAT-0017-01', clase: 'Escenario', detalle: 'Dado, cuando, entonces' },
      { id: 'DSN-MAT-0004-v1', clase: 'Modelo', detalle: 'Matrícula, Estudiante, Comprobante' },
      { id: 'CDU-MAT-0031-v1', clase: 'Código', detalle: 'Servicio, repositorio, esquema' },
      { id: 'TST-MAT-0017-01', clase: 'Prueba', detalle: 'Derivada del escenario' },
    ],
  };

  ngOnInit(): void {
    this.service.load().subscribe({
      next: (info) => {
        this.info.set(info);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }
}
