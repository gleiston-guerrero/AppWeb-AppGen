import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import { conservarPosicion } from '../shared/desplazamiento';
import { FeatureSettings, Provider } from './ai-settings';
import { AiSettingsService } from './ai-settings-service';

/**
 * Configuración del servicio de IA, una por función.
 *
 * Las funciones no piden lo mismo: validar requisitos son peticiones cortas y
 * frecuentes, donde interesa un modelo barato; generar casos de uso son pocas
 * peticiones largas, donde interesa el mejor. Un único proveedor para todo
 * obliga a pagar el caro en lo que no lo necesita.
 */
@Component({
  selector: 'slcp-ai-settings-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './ai-settings-page.html',
  styleUrl: './ai-settings-page.css',
})
export class AiSettingsPage implements OnInit {
  private readonly service = inject(AiSettingsService);
  private readonly proyectos = inject(ProjectService);
  private readonly ruta = inject(ActivatedRoute);

  protected projectId = '';
  protected readonly funciones = signal<FeatureSettings[]>([]);
  protected readonly proveedores = signal<Provider[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly esFacilitador = signal(false);

  /** Qué función se está editando. Solo una a la vez. */
  protected readonly editando = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly probando = signal<string | null>(null);
  protected readonly resultado = signal<Record<string, string>>({});

  protected proveedorElegido = 'ANTHROPIC';
  protected modelo = '';
  protected direccion = '';
  protected clave = '';

  /** Si se copia lo guardado al resto de funciones sin configurar. */
  protected aplicarATodas = false;

  protected readonly dondeLaClave = computed(
    () => this.proveedores().find((p) => p.id === this.proveedorElegido)?.keysUrl ?? '',
  );

  /** Funciones que no pueden realizarse sin modelo y no lo tienen. */
  protected readonly imprescindiblesSinConfigurar = computed(() =>
    this.funciones().filter((f) => f.essential && !f.enabled),
  );

  protected readonly activas = computed(() => this.funciones().filter((f) => f.enabled).length);

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';

    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esFacilitador.set(p?.myRoles.includes('PROJECT_FACILITATOR') ?? false);
      },
      error: () => this.esFacilitador.set(false),
    });

    this.service.proveedores(this.projectId).subscribe({
      next: (l) => this.proveedores.set(l),
      error: () => this.proveedores.set([]),
    });

    this.cargar();
  }

  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);

    this.service.funciones(this.projectId).subscribe({
      next: (l) => {
        this.funciones.set(l);
        this.cargando.set(false);
        volver();
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  // --- Edición ---

  protected editar(f: FeatureSettings): void {
    this.editando.set(f.feature);
    this.proveedorElegido = f.provider;
    this.modelo = f.model;
    this.direccion = f.baseUrl;
    this.clave = '';
    this.aplicarATodas = false;
    this.error.set(null);
  }

  /** Al cambiar de proveedor se proponen su dirección y su modelo habituales. */
  protected cambiarProveedor(id: string): void {
    this.proveedorElegido = id;
    const p = this.proveedores().find((x) => x.id === id);

    if (p) {
      this.modelo = p.defaultModel;
      this.direccion = p.defaultUrl;
    }
    // La clave del proveedor anterior no vale en el nuevo.
    this.clave = '';
  }

  protected guardar(f: FeatureSettings): void {
    this.guardando.set(true);
    this.error.set(null);

    const datos = {
      provider: this.proveedorElegido,
      model: this.modelo.trim(),
      baseUrl: this.direccion.trim(),
      apiKey: this.clave.trim() || undefined,
    };

    // Si se pidió aplicar a todas, se copia a las que aún no tienen credencial:
    // sobrescribir las que sí la tienen borraría una decisión ya tomada.
    const destinos = this.aplicarATodas
      ? [f.feature, ...this.funciones().filter((x) => !x.hasKey && x.feature !== f.feature)
          .map((x) => x.feature)]
      : [f.feature];

    let pendientes = destinos.length;
    let fallo: HttpErrorResponse | null = null;

    for (const destino of destinos) {
      this.service.guardar(this.projectId, destino, datos).subscribe({
        next: () => this.terminar(--pendientes, fallo, destinos.length),
        error: (e: HttpErrorResponse) => {
          fallo = e;
          this.terminar(--pendientes, fallo, destinos.length);
        },
      });
    }

    // La clave se borra del formulario en cuanto se envía: dejarla escrita la
    // expone a quien mire la pantalla después.
    this.clave = '';
  }

  private terminar(pendientes: number, fallo: HttpErrorResponse | null, total: number): void {
    if (pendientes > 0) {
      return;
    }
    this.guardando.set(false);
    this.editando.set(null);

    if (fallo) {
      this.error.set(this.explicar(fallo));
    } else {
      this.aviso.set(
        total === 1 ? 'Configuración guardada.' : `Configuración guardada en ${total} funciones.`,
      );
    }
    this.cargar();
  }

  protected activar(f: FeatureSettings, activo: boolean): void {
    this.service.activar(this.projectId, f.feature, activo).subscribe({
      next: () => {
        this.aviso.set(
          activo ? `${f.featureLabel}: asistencia activada.` : `${f.featureLabel}: desactivada.`,
        );
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected retirar(f: FeatureSettings): void {
    this.service.retirarCredencial(this.projectId, f.feature).subscribe({
      next: () => {
        this.aviso.set(`${f.featureLabel}: credencial retirada.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  /** Comprueba la configuración antes de depender de ella. */
  protected probar(f: FeatureSettings): void {
    this.probando.set(f.feature);

    this.service.probar(this.projectId, f.feature).subscribe({
      next: (r) => {
        this.probando.set(null);
        this.resultado.set({ ...this.resultado(), [f.feature]: r.message });
      },
      error: (fallo: HttpErrorResponse) => {
        this.probando.set(null);
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
    if (cuerpo?.message) {
      return cuerpo.message;
    }
    if (fallo.status === 403) {
      return 'Configurar el servicio de IA corresponde al facilitador del proyecto: es quien responde del gasto que genere.';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
