import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { SessionService } from '../auth/session-service';
import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import {
  ETIQUETA_ESTADO,
  ImportProfile,
  ImportResult,
  Requirement,
  RequirementSummary,
  Suggestion,
} from './requirement';
import { RequirementService } from './requirement-service';

/**
 * Requisitos de un proyecto: cargar, redactar, revisar y aprobar.
 *
 * Lo que la plataforma señala y lo que propone se presenta junto al texto
 * original y nunca en su lugar (ANA-14), y cada propuesta se acepta por
 * separado (ANA-15).
 */
@Component({
  selector: 'slcp-requirements-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './requirements-page.html',
  styleUrl: './requirements-page.css',
})
export class RequirementsPage implements OnInit {
  private readonly service = inject(RequirementService);
  private readonly proyectos = inject(ProjectService);
  private readonly sesion = inject(SessionService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly etiquetaEstado = ETIQUETA_ESTADO;

  protected projectId = '';
  protected readonly requisitos = signal<Requirement[]>([]);
  protected readonly resumen = signal<RequirementSummary | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  /** Roles propios en este proyecto, para saber qué ofrecer. */
  protected readonly esEquipo = signal(false);
  protected readonly esPropietario = signal(false);

  /** Filtro de la lista. */
  protected filtro: 'TODOS' | 'CON_HALLAZGOS' | 'SIN_CRITERIO' | 'APROBADOS' = 'TODOS';

  protected readonly visibles = computed(() => {
    const todos = this.requisitos();
    switch (this.filtro) {
      case 'CON_HALLAZGOS':
        return todos.filter((r) => r.findings.length > 0);
      case 'SIN_CRITERIO':
        return todos.filter((r) => !r.verification);
      case 'APROBADOS':
        return todos.filter((r) => r.status === 'APPROVED');
      default:
        return todos;
    }
  });

  /** Importación. */
  protected readonly importando = signal(false);
  protected readonly resultadoImport = signal<ImportResult | null>(null);
  protected contenido = '';
  protected nombreArchivo = '';
  protected perfil = '';

  /** Formatos admitidos y el elegido, con su ejemplo. */
  protected readonly formatos = signal<ImportProfile[]>([]);
  protected readonly formatoElegido = computed(() =>
    this.formatos().find((f) => f.id === this.perfil) ?? null,
  );

  /** Extensiones que acepta el selector de archivo, según el formato elegido. */
  protected readonly extensionesAceptadas = computed(() => {
    const f = this.formatoElegido();
    return f ? f.extensions.join(',') : '.tex,.md,.txt';
  });

  /** Alta manual. */
  protected readonly redactando = signal(false);
  protected nuevoEnunciado = '';
  protected nuevoCriterio = '';
  protected nuevoNombre = '';
  protected nuevoTipo = 'FUNCTIONAL';

  /** Requisito desplegado y edición en curso. */
  protected readonly abierto = signal<string | null>(null);
  protected readonly guardando = signal<string | null>(null);
  protected edicionCriterio = '';

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';
    this.cargarRoles();
    this.cargarFormatos();
    this.cargar();
  }

  private cargarFormatos(): void {
    this.service.formatos().subscribe({
      next: (lista) => {
        this.formatos.set(lista);
        if (!this.perfil && lista.length > 0) {
          this.perfil = lista[0].id;
        }
      },
      error: () => this.formatos.set([]),
    });
  }

  /** Al cambiar de formato, el archivo elegido deja de ser válido para él. */
  protected cambiarFormato(): void {
    this.contenido = '';
    this.nombreArchivo = '';
    this.resultadoImport.set(null);
  }

  private cargarRoles(): void {
    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esEquipo.set(p?.myRoles.includes('TEAM_MEMBER') ?? false);
        this.esPropietario.set(p?.myRoles.includes('PRODUCT_OWNER') ?? false);
      },
      error: () => {
        this.esEquipo.set(false);
        this.esPropietario.set(false);
      },
    });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.service.listar(this.projectId).subscribe({
      next: (lista) => {
        this.requisitos.set(lista);
        this.cargando.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
    this.service.resumen(this.projectId).subscribe({
      next: (r) => this.resumen.set(r),
      error: () => this.resumen.set(null),
    });
  }

  // ---- Importación ----

  protected archivoElegido(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    if (!archivo) {
      return;
    }
    this.nombreArchivo = archivo.name;
    archivo.text().then((texto) => (this.contenido = texto));
  }

  protected importar(): void {
    if (this.contenido.trim().length === 0) {
      this.error.set('Elija un archivo o pegue el contenido antes de importar.');
      return;
    }
    this.importando.set(true);
    this.error.set(null);

    this.service.importar(this.projectId, this.perfil, this.contenido).subscribe({
      next: (r) => {
        this.importando.set(false);
        this.resultadoImport.set(r);
        this.contenido = '';
        this.nombreArchivo = '';
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.importando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  // ---- Alta manual ----

  protected crear(): void {
    if (this.nuevoEnunciado.trim().length === 0) {
      this.error.set('El enunciado es obligatorio.');
      return;
    }
    this.redactando.set(true);
    this.error.set(null);

    this.service
      .crear(this.projectId, {
        kind: this.nuevoTipo,
        name: this.nuevoNombre.trim() || undefined,
        statement: this.nuevoEnunciado.trim(),
        verification: this.nuevoCriterio.trim() || undefined,
      })
      .subscribe({
        next: (r) => {
          this.redactando.set(false);
          this.nuevoEnunciado = '';
          this.nuevoCriterio = '';
          this.nuevoNombre = '';
          this.aviso.set(`Requisito ${r.readableId} creado en borrador.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => {
          this.redactando.set(false);
          this.error.set(this.explicar(fallo));
        },
      });
  }

  // ---- Revisión ----

  protected alternar(r: Requirement): void {
    this.abierto.set(this.abierto() === r.readableId ? null : r.readableId);
    this.edicionCriterio = r.verification ?? '';
  }

  /** Acepta una propuesta tal cual, marcando su procedencia (ANA-16). */
  protected aceptar(r: Requirement, s: Suggestion): void {
    this.aplicar(r, s.text, true);
  }

  /** Copia la propuesta al campo para modificarla antes de aceptarla (ANA-21). */
  protected modificar(s: Suggestion): void {
    this.edicionCriterio = s.text;
  }

  /** Guarda lo que la persona escribió: procedencia humana. */
  protected guardarCriterio(r: Requirement): void {
    this.aplicar(r, this.edicionCriterio, false);
  }

  private aplicar(r: Requirement, criterio: string, deSugerencia: boolean): void {
    this.guardando.set(r.readableId);
    this.error.set(null);

    this.service
      .editar(
        this.projectId,
        r.readableId,
        { statement: r.statement, verification: criterio },
        deSugerencia,
      )
      .subscribe({
        next: () => {
          this.guardando.set(null);
          this.aviso.set(
            deSugerencia
              ? `Propuesta aceptada en ${r.readableId}. Queda registrada su procedencia.`
              : `Criterio guardado en ${r.readableId}.`,
          );
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => {
          this.guardando.set(null);
          this.error.set(this.explicar(fallo));
        },
      });
  }

  protected transitar(r: Requirement, destino: string): void {
    this.guardando.set(r.readableId);
    this.service.transitar(this.projectId, r.readableId, destino).subscribe({
      next: () => {
        this.guardando.set(null);
        this.aviso.set(`${r.readableId}: ${this.etiquetaEstado[destino] ?? destino}.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.guardando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected defectos(r: Requirement): number {
    return r.findings.filter((f) => f.severity === 'DEFECTO').length;
  }

  private explicar(fallo: HttpErrorResponse): string {
    if (fallo.status === 0) {
      return 'El servicio no responde. Compruebe que está en marcha en el puerto 8081.';
    }
    const cuerpo = fallo.error as ApiError | null;
    return cuerpo?.message ?? `El servicio devolvió un error ${fallo.status}.`;
  }
}
