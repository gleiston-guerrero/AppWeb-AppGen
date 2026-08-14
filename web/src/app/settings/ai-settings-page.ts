import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import { conservarPosicion } from '../shared/desplazamiento';
import { Credential, FeatureSettings, Prompt, Provider } from './ai-settings';
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
  protected readonly credenciales = signal<Credential[]>([]);
  protected readonly prompts = signal<Prompt[]>([]);
  protected readonly proveedores = signal<Provider[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly esFacilitador = signal(false);

  /** Qué función se está editando. Solo una a la vez. */
  /** Qué credencial se está editando, por proveedor. */
  protected readonly editandoCredencial = signal<string | null>(null);

  /** Qué instrucción se está editando, por función. */
  protected readonly editandoPrompt = signal<string | null>(null);
  protected instruccion = '';
  protected readonly editando = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly probando = signal<string | null>(null);
  protected readonly resultado = signal<Record<string, string>>({});

  protected proveedorElegido = 'ANTHROPIC';
  protected modelo = '';
  protected direccion = '';
  protected clave = '';

  /** Si se copia lo guardado al resto de funciones sin configurar. */

  protected readonly dondeLaClave = computed(
    () => this.proveedores().find((p) => p.id === this.proveedorElegido)?.keysUrl ?? '',
  );

  /** Funciones que no pueden realizarse sin modelo y no lo tienen. */
  protected readonly imprescindiblesSinConfigurar = computed(() =>
    this.funciones().filter((f) => f.essential && !f.enabled),
  );

  protected readonly activas = computed(() => this.funciones().filter((f) => f.enabled).length);

  /** Proveedores sin credencial: los que aún pueden añadirse. */
  protected readonly sinGuardar = computed(() => {
    const guardados = new Set(this.credenciales().map((c) => c.provider));
    return this.proveedores().filter((p) => !guardados.has(p.id));
  });

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

    this.service.prompts(this.projectId).subscribe({
      next: (l) => this.prompts.set(l),
      error: () => this.prompts.set([]),
    });

    this.service.credenciales(this.projectId).subscribe({
      next: (l) => this.credenciales.set(l),
      error: () => this.credenciales.set([]),
    });

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

  // --- Credenciales, una por proveedor ---

  /** Al cambiar de proveedor se proponen su dirección y su modelo habituales. */
  protected cambiarProveedor(id: string): void {
    this.proveedorElegido = id;

    const guardada = this.credenciales().find((c) => c.provider === id);
    const p = this.proveedores().find((x) => x.id === id);

    this.modelo = guardada?.model ?? p?.defaultModel ?? '';
    this.direccion = guardada?.baseUrl ?? p?.defaultUrl ?? '';
    // La clave del proveedor anterior no vale en el nuevo.
    this.clave = '';
  }

  protected editarCredencial(providerId: string): void {
    this.editandoCredencial.set(providerId);
    this.editando.set(null);

    const c = this.credenciales().find((x) => x.provider === providerId);
    const p = this.proveedores().find((x) => x.id === providerId);

    this.proveedorElegido = providerId;
    this.modelo = c?.model ?? p?.defaultModel ?? '';
    this.direccion = c?.baseUrl ?? p?.defaultUrl ?? '';
    this.clave = '';
    this.error.set(null);
  }

  protected guardarCredencial(): void {
    this.guardando.set(true);
    this.error.set(null);

    this.service
      .guardarCredencial(this.projectId, this.proveedorElegido, {
        model: this.modelo.trim(),
        baseUrl: this.direccion.trim(),
        apiKey: this.clave.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editandoCredencial.set(null);
          // La clave se borra del formulario en cuanto se envía: dejarla escrita
          // la expone a quien mire la pantalla después.
          this.clave = '';
          this.aviso.set('Credencial guardada. Vale para todas las funciones que usen ese proveedor.');
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => {
          this.guardando.set(false);
          this.error.set(this.explicar(fallo));
        },
      });
  }

  protected retirarCredencial(c: Credential): void {
    this.service.retirarCredencial(this.projectId, c.provider).subscribe({
      next: () => {
        this.aviso.set(
          `Credencial de ${c.providerLabel} retirada. Las funciones que la usaban quedan desactivadas.`,
        );
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected probar(c: Credential): void {
    this.probando.set(c.provider);

    this.service.probar(this.projectId, c.provider).subscribe({
      next: (r) => {
        this.probando.set(null);
        this.resultado.set({ ...this.resultado(), [c.provider]: r.message });
      },
      error: (fallo: HttpErrorResponse) => {
        this.probando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  // --- Instrucciones, una por función ---

  protected editarPrompt(p: Prompt): void {
    this.editandoPrompt.set(p.feature);
    this.editandoCredencial.set(null);
    this.instruccion = p.template;
    this.error.set(null);
  }

  /** Devuelve el texto a la de fábrica sin guardarlo todavía. */
  protected verFabrica(p: Prompt): void {
    this.instruccion = p.defaultTemplate;
  }

  protected marcas(p: Prompt): { marca: string; que: string }[] {
    return Object.entries(p.placeholders).map(([marca, que]) => ({ marca, que }));
  }

  /** Marcas que la instrucción editada ha perdido. */
  protected marcasPerdidas(p: Prompt): string[] {
    return Object.keys(p.placeholders).filter((m) => !this.instruccion.includes(m));
  }

  protected guardarPrompt(p: Prompt): void {
    this.guardando.set(true);
    this.error.set(null);

    this.service.guardarPrompt(this.projectId, p.feature, this.instruccion).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editandoPrompt.set(null);
        this.aviso.set(
          `Instrucción de ${p.featureLabel} guardada. La usarán todos los proveedores de esa función.`,
        );
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected restaurarPrompt(p: Prompt): void {
    this.service.restaurarPrompt(this.projectId, p.feature).subscribe({
      next: () => {
        this.editandoPrompt.set(null);
        this.aviso.set(`Instrucción de ${p.featureLabel} devuelta a la de fábrica.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  // --- Qué proveedor sirve a cada función ---

  protected elegirProveedor(f: FeatureSettings, provider: string): void {
    this.service.elegirProveedor(this.projectId, f.feature, provider).subscribe({
      next: () => {
        this.aviso.set(`${f.featureLabel} usará ${provider}.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
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
