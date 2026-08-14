import { HttpErrorResponse } from '@angular/common/http';

import { conservarPosicion } from '../shared/desplazamiento';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ETIQUETA_ESTADO } from '../requirements/requirement';
import { ApiError } from '../registration/registration';
import { ReportRow, RequirementReport } from './report';
import { ReportService } from './report-service';

/**
 * Informe de los requisitos con todo lo que consta de ellos.
 *
 * Reúne lo que en las pantallas está repartido. Repartido se consulta bien de
 * uno en uno; junto se ve lo que solo aparece al mirar el conjunto: cuántos
 * carecen de criterio, cuántos no tienen ninguna prueba.
 */
@Component({
  selector: 'slcp-requirements-report-page',
  imports: [RouterLink],
  templateUrl: './requirements-report-page.html',
  styleUrl: './requirements-report-page.css',
})
export class RequirementsReportPage implements OnInit {
  private readonly service = inject(ReportService);
  private readonly ruta = inject(ActivatedRoute);

  /** Se reutiliza la tabla de la pantalla de requisitos: una sola traducción. */
  protected readonly etiquetaEstado = ETIQUETA_ESTADO;

  protected projectId = '';
  protected readonly informe = signal<RequirementReport | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  /** Qué requisitos se muestran. Vacío es todos. */
  protected readonly filtro = signal<'TODOS' | 'SIN_CRITERIO' | 'SIN_PRUEBAS' | 'CON_HALLAZGOS'>(
    'TODOS',
  );

  /** Si se muestra el detalle largo o solo la tabla. */
  protected readonly detallado = signal(true);
  protected readonly descargando = signal(false);

  protected readonly filas = computed<ReportRow[]>(() => {
    const r = this.informe();
    if (!r) {
      return [];
    }

    return r.rows.filter((f) => {
      switch (this.filtro()) {
        case 'SIN_CRITERIO':
          return !f.verification;
        case 'SIN_PRUEBAS':
          return f.tests === 0;
        case 'CON_HALLAZGOS':
          return f.findings.length > 0;
        default:
          return true;
      }
    });
  });

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';
    this.cargar();
  }

  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);
    this.service.informe(this.projectId).subscribe({
      next: (r) => {
        this.informe.set(r);
        this.cargando.set(false);
        volver();
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  protected fecha(iso: string): string {
    return new Date(iso).toLocaleString('es-EC');
  }

  /**
   * Imprime el informe.
   *
   * Se usa la impresión del navegador en lugar de generar un PDF en el servidor:
   * el navegador ya sabe paginar y respeta los estilos de impresión, y guardar
   * como PDF es una opción del propio diálogo.
   */
  protected imprimir(): void {
    window.print();
  }

  /**
   * Descarga el informe.
   *
   * El archivo lo arma el servicio: llevárselo corresponde al equipo y al
   * facilitador, y una restricción que se cumpliera solo en la pantalla no
   * restringiría nada, porque los datos ya están aquí.
   */
  protected descargar(): void {
    const r = this.informe();
    if (!r) {
      return;
    }
    this.descargando.set(true);

    this.service.exportar(this.projectId).subscribe({
      next: (blob) => {
        this.descargando.set(false);

        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `requisitos-${r.projectId}.csv`;
        enlace.click();
        URL.revokeObjectURL(url);

        this.aviso.set('Informe descargado.');
      },
      error: (fallo: HttpErrorResponse) => {
        this.descargando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    if (fallo.status === 401) {
      return 'Su sesión ha caducado. Vuelva a entrar.';
    }

    const cuerpo = fallo.error as ApiError | null;
    return cuerpo?.message ?? `El servicio devolvió un error ${fallo.status}.`;
  }
}
