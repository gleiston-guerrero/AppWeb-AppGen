import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import { Credential } from '../settings/ai-settings';
import { AiSettingsService } from '../settings/ai-settings-service';
import { conservarPosicion } from '../shared/desplazamiento';
import {
  BenchmarkResult,
  BenchmarkRun,
  CLASES_POR_FUNCION,
  FUNCIONES_ENSAYABLES,
} from './benchmark';
import { BenchmarkService } from './benchmark-service';

/**
 * Ensayo comparativo de proveedores sobre los requisitos del proyecto.
 *
 * Las comparativas publicadas miden con conjuntos de propósito general, no con
 * requisitos de un dominio concreto en castellano. Aquí se mide sobre los del
 * proyecto, que es lo único que dice algo del caso propio.
 */
@Component({
  selector: 'slcp-benchmark-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './benchmark-page.html',
  styleUrl: './benchmark-page.css',
})
export class BenchmarkPage implements OnInit {
  private readonly service = inject(BenchmarkService);
  private readonly ajustes = inject(AiSettingsService);
  private readonly proyectos = inject(ProjectService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly FUNCIONES = FUNCIONES_ENSAYABLES;

  protected projectId = '';
  protected readonly ensayos = signal<BenchmarkRun[]>([]);
  /**
   * Credenciales guardadas del proyecto.
   *
   * El ensayo compara proveedores, no funciones: basta con que tengan credencial,
   * sin importar cuál esté activo. Es lo que permite comparar cuatro a la vez.
   */
  protected readonly configuradas = signal<Credential[]>([]);
  protected readonly cargando = signal(true);
  protected readonly ejecutando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly esEquipo = signal(false);

  protected readonly abierto = signal<string | null>(null);

  protected funcion = 'GENERATE_TESTS';
  protected clase = 'ACCEPTANCE';
  protected notas = '';
  protected elegidos = new Set<string>();

  protected readonly clases = computed(() => CLASES_POR_FUNCION[this.funcion] ?? []);

  /**
   * Proveedores que pueden ensayarse en la función elegida.
   *
   * Solo los que tienen credencial guardada para ella: llamar a uno sin
   * configurar solo produciría una columna de fallos.
   */
  protected readonly disponibles = computed(() => this.configuradas());

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';

    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esEquipo.set(p?.myRoles.includes('TEAM_MEMBER') ?? false);
      },
      error: () => this.esEquipo.set(false),
    });

    this.ajustes.credenciales(this.projectId).subscribe({
      next: (l) => this.configuradas.set(l),
      error: () => this.configuradas.set([]),
    });

    this.cargar();
  }

  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);

    this.service.historial(this.projectId).subscribe({
      next: (l) => {
        this.ensayos.set(l);
        this.cargando.set(false);
        volver();
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  protected cambiarFuncion(f: string): void {
    this.funcion = f;
    this.clase = CLASES_POR_FUNCION[f]?.[0]?.id ?? '';
  }

  protected alternarProveedor(id: string): void {
    if (this.elegidos.has(id)) {
      this.elegidos.delete(id);
    } else {
      this.elegidos.add(id);
    }
  }

  protected elegido(id: string): boolean {
    return this.elegidos.has(id);
  }

  protected ejecutar(): void {
    if (this.elegidos.size < 2) {
      this.error.set(
        'Un ensayo compara: elija al menos dos proveedores. Con uno solo se obtiene una medida sin nada con que contrastarla.',
      );
      return;
    }

    this.ejecutando.set(true);
    this.error.set(null);

    this.service
      .ejecutar(this.projectId, {
        feature: this.funcion,
        subkind: this.clase,
        requirements: [],
        providers: [...this.elegidos],
        notes: this.notas.trim(),
      })
      .subscribe({
        next: (r) => {
          this.ejecutando.set(false);
          this.abierto.set(r.id);
          this.aviso.set('Ensayo terminado. Las medidas son cuentas, no juicios de calidad.');
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => {
          this.ejecutando.set(false);
          this.error.set(this.explicar(fallo));
        },
      });
  }

  protected alternar(r: BenchmarkRun): void {
    this.abierto.set(this.abierto() === r.id ? null : r.id);
  }

  protected fecha(iso: string): string {
    return new Date(iso).toLocaleString('es-EC');
  }

  /** Proporción de propuestas sin huecos, que es la medida más comparable. */
  protected completas(r: BenchmarkResult): string {
    return r.produced === 0 ? '—' : `${Math.round((r.complete / r.produced) * 100)}%`;
  }

  protected segundos(ms: number): string {
    return (ms / 1000).toFixed(1) + ' s';
  }

  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    if (fallo.status === 401) {
      return 'Su sesión ha caducado. Vuelva a entrar.';
    }

    const cuerpo = fallo.error as ApiError | null;
    if (cuerpo?.message) {
      return cuerpo.message;
    }
    if (fallo.status === 403) {
      return 'El ensayo lo ejecuta el equipo de desarrollo: consume cuota de los servicios configurados.';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
