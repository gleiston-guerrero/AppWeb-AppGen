import { Component, OnInit, inject, signal } from '@angular/core';

import { PlatformInfo } from './platform-info';
import { PlatformInfoService } from './platform-info-service';

/**
 * Pantalla publica de informacion de la plataforma.
 *
 * Realiza FUN-01 y FUN-02: muestra sin sesion iniciada como esta construida la
 * plataforma, que es capaz de producir, cual es su insumo de entrada, su
 * autoria y su licencia. No presenta dato alguno de ningun proyecto.
 */
@Component({
  selector: 'slcp-platform-info-page',
  templateUrl: './platform-info-page.html',
  styleUrl: './platform-info-page.css',
})
export class PlatformInfoPage implements OnInit {
  private readonly service = inject(PlatformInfoService);

  protected readonly info = signal<PlatformInfo | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.service.load().subscribe({
      next: (info) => {
        this.info.set(info);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(
          'No se pudo contactar con el servicio. Compruebe que esta en marcha en el puerto 8081.',
        );
        this.cargando.set(false);
      },
    });
  }
}
