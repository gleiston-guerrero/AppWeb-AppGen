import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import { conservarPosicion } from '../shared/desplazamiento';
import {
  CASO_DE_USO_VACIO,
  HISTORIA_VACIA,
  Specification,
  SpecificationsState,
} from './specification';
import { SpecificationService } from './specification-service';

/**
 * Casos de uso expandidos e historias de usuario.
 *
 * Se generan con modelo, se editan, se escriben desde cero, y lo que el equipo
 * acepta pasa a ser regla base: se conserva aunque se vuelva a generar.
 *
 * Los campos son los de las tablas 8 y 9 del manuscrito. Se editan como
 * documento porque los flujos son listas de pasos a dos columnas: un formulario
 * con veinte campos fijos no podría representarlos sin inventar una estructura
 * distinta de la que se guarda.
 */
@Component({
  selector: 'slcp-specifications-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './specifications-page.html',
  styleUrl: './specifications-page.css',
})
export class SpecificationsPage implements OnInit {
  private readonly service = inject(SpecificationService);
  private readonly proyectos = inject(ProjectService);
  private readonly ruta = inject(ActivatedRoute);

  protected projectId = '';
  protected readonly estado = signal<SpecificationsState | null>(null);
  protected readonly cargando = signal(true);
  protected readonly generando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly esEquipo = signal(false);

  /** Qué pestaña se muestra. */
  protected readonly vista = signal<'USE_CASE' | 'USER_STORY'>('USE_CASE');

  protected readonly abierto = signal<string | null>(null);
  protected readonly editando = signal<string | null>(null);
  protected readonly creando = signal(false);

  protected nombre = '';
  protected campos = '';
  protected requisitos = '';

  protected readonly visibles = computed<Specification[]>(() => {
    const e = this.estado();
    if (!e) {
      return [];
    }
    return this.vista() === 'USE_CASE' ? e.useCases : e.userStories;
  });

  /** Reglas base que se quedaron atrás: su requisito cambió. */
  protected readonly atrasadas = computed(() => this.visibles().filter((s) => s.outdated));

  protected readonly reglasBase = computed(
    () => this.visibles().filter((s) => s.baseline).length,
  );

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';

    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esEquipo.set(p?.myRoles.includes('TEAM_MEMBER') ?? false);
      },
      error: () => this.esEquipo.set(false),
    });

    this.cargar();
  }

  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);

    this.service.estado(this.projectId).subscribe({
      next: (e) => {
        this.estado.set(e);
        this.cargando.set(false);
        volver();
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  protected alternar(s: Specification): void {
    this.abierto.set(this.abierto() === s.readableId ? null : s.readableId);
  }

  /** Presenta los campos con sangría, que sin ella son ilegibles. */
  protected formatear(campos: string): string {
    try {
      return JSON.stringify(JSON.parse(campos), null, 2);
    } catch {
      return campos;
    }
  }

  // --- Generación con modelo ---

  protected generar(): void {
    this.generando.set(true);
    this.error.set(null);

    this.service.generar(this.projectId, { kind: this.vista(), requirements: [] }).subscribe({
      next: (nuevas) => {
        this.generando.set(false);
        this.aviso.set(
          `${nuevas.length} propuestas generadas. Léalas antes de aceptarlas: la acción del actor no está en ningún requisito y procede de lo que el modelo infirió.`,
        );
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.generando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  // --- Escritura sin modelo ---

  protected empezarDeCero(): void {
    this.creando.set(true);
    this.editando.set(null);
    this.nombre = '';
    this.requisitos = '';
    this.campos = JSON.stringify(
      this.vista() === 'USE_CASE' ? CASO_DE_USO_VACIO : HISTORIA_VACIA,
      null,
      2,
    );
    this.error.set(null);
  }

  protected abrirEdicion(s: Specification): void {
    this.editando.set(s.readableId);
    this.creando.set(false);
    this.nombre = s.name;
    this.campos = this.formatear(s.fields);
    this.requisitos = s.requirements.join(', ');
    this.abierto.set(s.readableId);
    this.error.set(null);
  }

  protected guardar(s: Specification | null): void {
    if (this.nombre.trim().length === 0) {
      this.error.set('El nombre es obligatorio.');
      return;
    }
    if (!this.esDocumentoValido()) {
      this.error.set(
        'Los campos no forman un documento válido. Revise las comas y las llaves: se guarda tal cual, y si no puede leerse, la comprobación tampoco.',
      );
      return;
    }

    this.guardando.set(true);
    const datos = {
      kind: this.vista(),
      name: this.nombre.trim(),
      fields: this.campos,
      requirements: this.requisitos
        .split(',')
        .map((r) => r.trim())
        .filter((r) => r.length > 0),
    };

    const peticion = s
      ? this.service.editar(this.projectId, s.readableId, datos)
      : this.service.crear(this.projectId, datos);

    peticion.subscribe({
      next: (r) => {
        this.guardando.set(false);
        this.editando.set(null);
        this.creando.set(false);
        this.aviso.set(
          s
            ? `${r.readableId} actualizada. Pasa a constar escrita por una persona y deja de ser regla base: lo aceptado era otro texto.`
            : `${r.readableId} creada.`,
        );
        this.abierto.set(r.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  private esDocumentoValido(): boolean {
    try {
      JSON.parse(this.campos);
      return true;
    } catch {
      return false;
    }
  }

  // --- Decisión del equipo ---

  protected aceptar(s: Specification): void {
    this.service.aceptar(this.projectId, s.readableId).subscribe({
      next: (r) => {
        this.aviso.set(
          r.acceptedWithIssues > 0
            ? `${r.readableId} aceptada con ${r.acceptedWithIssues} reparos pendientes. Queda constancia.`
            : `${r.readableId} aceptada. Es regla base: se conservará aunque se vuelva a generar.`,
        );
        this.abierto.set(s.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected descartar(s: Specification): void {
    this.service.descartar(this.projectId, s.readableId).subscribe({
      next: () => {
        this.aviso.set(`${s.readableId} descartada.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected retirarReglaBase(s: Specification): void {
    this.service.retirarReglaBase(this.projectId, s.readableId).subscribe({
      next: () => {
        this.aviso.set(`${s.readableId} deja de ser regla base. Ya puede regenerarse.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected eliminar(s: Specification): void {
    this.service.eliminar(this.projectId, s.readableId).subscribe({
      next: () => {
        this.aviso.set(`${s.readableId} eliminada.`);
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
      return 'Los casos de uso y las historias los produce y acepta el equipo de desarrollo.';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
