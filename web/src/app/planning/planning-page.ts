import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { conservarPosicion } from '../shared/desplazamiento';

import { Member } from '../projects/project';
import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import {
  Activity,
  CLASES_DE_RECURSO,
  Component as Componente,
  DeliverableBreakdown,
  Plan,
  Task,
} from './plan';
import { PlanService } from './plan-service';

/**
 * Descomposición del trabajo: componentes, tareas, actividades y recursos.
 *
 * Todo el avance que se muestra viene calculado del servidor. No hay ningún
 * campo donde escribirlo, y esa ausencia es deliberada: un porcentaje escrito a
 * mano es una opinión con apariencia de medida.
 */
@Component({
  selector: 'slcp-planning-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './planning-page.html',
  styleUrl: './planning-page.css',
})
export class PlanningPage implements OnInit {
  private readonly service = inject(PlanService);
  private readonly proyectos = inject(ProjectService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly CLASES = CLASES_DE_RECURSO;

  protected projectId = '';
  protected readonly plan = signal<Plan | null>(null);
  protected readonly equipo = signal<Member[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly esFacilitador = signal(false);
  protected readonly esEquipo = signal(false);

  /** Qué está desplegado. Se guarda por identificador, no por posición. */
  protected readonly entregableAbierto = signal<string | null>(null);
  protected readonly tareaAbierta = signal<string | null>(null);

  /** Formularios en curso, identificados por el elemento al que pertenecen. */
  protected readonly creandoComponente = signal<string | null>(null);
  protected readonly creandoTarea = signal<string | null>(null);
  protected readonly creandoActividad = signal<string | null>(null);
  protected readonly registrandoHoras = signal<string | null>(null);
  protected readonly asignandoRecurso = signal<string | null>(null);
  protected readonly creandoRecurso = signal(false);

  /** Qué se está modificando. Es distinto de crear: el formulario nace con datos. */
  protected readonly editandoComponente = signal<string | null>(null);
  protected readonly editandoTarea = signal<string | null>(null);
  protected readonly editandoActividad = signal<string | null>(null);
  protected readonly editandoRecurso = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected nombreComponente = '';
  protected descripcionComponente = '';

  protected nombreTarea = '';
  protected esfuerzoTarea: number | null = null;
  protected responsableTarea = '';

  protected nombreActividad = '';
  protected esfuerzoActividad = 1;

  protected horas: number | null = null;
  protected fechaHoras = '';
  protected notaHoras = '';

  protected nombreRecurso = '';
  protected claseRecurso = 'EQUIPMENT';
  protected unidadRecurso = '';
  protected cantidadRecurso: number | null = null;

  protected recursoElegido = '';
  protected cantidadAsignada: number | null = null;

  /** Miembros del equipo, que son a quienes puede asignarse trabajo. */
  protected readonly asignables = computed(() =>
    this.equipo().filter((m) => m.role === 'TEAM_MEMBER'),
  );

  /** Personas que concentran más de un tercio del esfuerzo pendiente. */
  protected readonly concentracion = computed(() => {
    const p = this.plan();
    if (!p || p.effort === 0) {
      return [];
    }
    return p.workload.filter((w) => w.effort / p.effort > 0.34);
  });

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';
    this.cargarRoles();
    this.cargar();
  }

  private cargarRoles(): void {
    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esFacilitador.set(p?.myRoles.includes('PROJECT_FACILITATOR') ?? false);
        this.esEquipo.set(p?.myRoles.includes('TEAM_MEMBER') ?? false);
      },
      error: () => {
        this.esFacilitador.set(false);
        this.esEquipo.set(false);
      },
    });

    this.proyectos.equipo(this.projectId).subscribe({
      next: (lista) => this.equipo.set(lista),
      error: (fallo: HttpErrorResponse) => {
        // Un fallo no puede parecer "no hay nada": son cosas distintas y quien
        // mira no puede distinguirlas.
        this.equipo.set([]);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /**
   * Vuelve a pedir el plan.
   *
   * Conserva la posición de la página: tras marcar una actividad o registrar
   * horas, la lista se redibuja y sin esto quien estaba en la tarea decimoquinta
   * acabaría en la primera.
   */
  protected cargar(despues?: () => void): void {
    const volver = conservarPosicion();
    this.cargando.set(true);
    this.service.plan(this.projectId).subscribe({
      next: (p) => {
        this.plan.set(p);
        this.cargando.set(false);
        volver();
        if (despues) {
          setTimeout(despues, 0);
        }
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  // --- Presentación ---

  protected pct(valor: number): number {
    return Math.round(valor * 100);
  }

  protected alternarEntregable(d: DeliverableBreakdown): void {
    this.entregableAbierto.set(
      this.entregableAbierto() === d.deliverableId ? null : d.deliverableId,
    );
  }

  protected alternarTarea(t: Task): void {
    this.tareaAbierta.set(this.tareaAbierta() === t.readableId ? null : t.readableId);
  }

  // --- Componentes ---

  protected abrirComponente(d: DeliverableBreakdown): void {
    this.creandoComponente.set(d.deliverableId);
    this.nombreComponente = '';
    this.descripcionComponente = '';
    this.error.set(null);
  }

  protected guardarComponente(d: DeliverableBreakdown): void {
    if (this.nombreComponente.trim().length === 0) {
      this.error.set('El nombre del componente es obligatorio.');
      return;
    }
    this.guardando.set(true);

    this.service
      .crearComponente(this.projectId, d.deliverableId, {
        name: this.nombreComponente.trim(),
        description: this.descripcionComponente.trim() || undefined,
      })
      .subscribe({
        next: (c) => {
          this.guardando.set(false);
          this.creandoComponente.set(null);
          this.aviso.set(`Componente ${c.readableId} creado.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  /** Abre el formulario con los datos del componente, para modificarlo. */
  protected abrirEdicionComponente(c: Componente): void {
    this.editandoComponente.set(c.readableId);
    this.nombreComponente = c.name;
    this.descripcionComponente = c.description ?? '';
    this.error.set(null);
  }

  protected actualizarComponente(c: Componente): void {
    if (this.nombreComponente.trim().length === 0) {
      this.error.set('El nombre del componente es obligatorio.');
      return;
    }
    this.guardando.set(true);

    this.service
      .editarComponente(this.projectId, c.readableId, {
        name: this.nombreComponente.trim(),
        description: this.descripcionComponente.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editandoComponente.set(null);
          this.aviso.set(`Componente ${c.readableId} actualizado.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected eliminarComponente(c: Componente): void {
    this.service.eliminarComponente(this.projectId, c.readableId).subscribe({
      next: () => {
        this.aviso.set(`Componente ${c.readableId} eliminado.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  // --- Tareas ---

  protected abrirTarea(c: Componente): void {
    this.creandoTarea.set(c.readableId);
    this.nombreTarea = '';
    this.esfuerzoTarea = null;
    this.responsableTarea = '';
    this.error.set(null);
  }

  protected guardarTarea(c: Componente): void {
    if (this.nombreTarea.trim().length === 0) {
      this.error.set('El nombre de la tarea es obligatorio.');
      return;
    }
    if (!this.esfuerzoTarea || this.esfuerzoTarea <= 0) {
      this.error.set(
        'El esfuerzo previsto es obligatorio: sin él, el avance del componente se promediaría sin peso y terminar lo trivial dejando lo difícil daría un avance engañoso.',
      );
      return;
    }
    this.guardando.set(true);

    this.service
      .crearTarea(this.projectId, c.readableId, {
        name: this.nombreTarea.trim(),
        plannedEffort: this.esfuerzoTarea,
        assignee: this.responsableTarea || undefined,
      })
      .subscribe({
        next: (t) => {
          this.guardando.set(false);
          this.creandoTarea.set(null);
          this.aviso.set(`Tarea ${t.readableId} creada.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected abrirEdicionTarea(t: Task): void {
    this.editandoTarea.set(t.readableId);
    this.nombreTarea = t.name;
    this.esfuerzoTarea = t.plannedEffort;
    this.responsableTarea = t.assignee ?? '';
    this.error.set(null);
  }

  protected actualizarTarea(t: Task): void {
    if (this.nombreTarea.trim().length === 0) {
      this.error.set('El nombre de la tarea es obligatorio.');
      return;
    }
    if (!this.esfuerzoTarea || this.esfuerzoTarea <= 0) {
      this.error.set('El esfuerzo previsto ha de ser mayor que cero.');
      return;
    }
    this.guardando.set(true);

    this.service
      .editarTarea(this.projectId, t.readableId, {
        name: this.nombreTarea.trim(),
        plannedEffort: this.esfuerzoTarea,
        assignee: this.responsableTarea || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editandoTarea.set(null);
          this.aviso.set(`Tarea ${t.readableId} actualizada.`);
          this.tareaAbierta.set(t.readableId);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected transitarTarea(t: Task, destino: string): void {
    this.service.transitarTarea(this.projectId, t.readableId, destino).subscribe({
      next: (r) => {
        this.aviso.set(`${r.readableId}: ${r.statusLabel}.`);
        this.tareaAbierta.set(t.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  protected eliminarTarea(t: Task): void {
    this.service.eliminarTarea(this.projectId, t.readableId).subscribe({
      next: () => {
        this.aviso.set(`Tarea ${t.readableId} eliminada.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  // --- Actividades ---

  protected abrirActividad(t: Task): void {
    this.creandoActividad.set(t.readableId);
    this.nombreActividad = '';
    this.esfuerzoActividad = 1;
    this.error.set(null);
  }

  protected guardarActividad(t: Task): void {
    if (this.nombreActividad.trim().length === 0) {
      this.error.set('El nombre de la actividad es obligatorio.');
      return;
    }
    this.guardando.set(true);

    this.service
      .crearActividad(this.projectId, t.readableId, {
        name: this.nombreActividad.trim(),
        plannedEffort: this.esfuerzoActividad,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.creandoActividad.set(null);
          this.tareaAbierta.set(t.readableId);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected abrirEdicionActividad(a: Activity): void {
    this.editandoActividad.set(a.readableId);
    this.nombreActividad = a.name;
    this.esfuerzoActividad = a.plannedEffort;
    this.error.set(null);
  }

  protected actualizarActividad(t: Task, a: Activity): void {
    if (this.nombreActividad.trim().length === 0) {
      this.error.set('El nombre de la actividad es obligatorio.');
      return;
    }
    this.guardando.set(true);

    this.service
      .editarActividad(this.projectId, a.readableId, {
        name: this.nombreActividad.trim(),
        plannedEffort: this.esfuerzoActividad,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editandoActividad.set(null);
          this.tareaAbierta.set(t.readableId);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  /** Retira un asiento de horas mal anotado. Solo puede quien lo anotó. */
  protected retirarHoras(t: Task, a: Activity, entryId: string): void {
    this.service.retirarHoras(this.projectId, a.readableId, entryId).subscribe({
      next: () => {
        this.tareaAbierta.set(t.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  protected marcarActividad(t: Task, a: Activity): void {
    this.service.marcarActividad(this.projectId, a.readableId, !a.done).subscribe({
      next: () => {
        this.tareaAbierta.set(t.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  protected eliminarActividad(t: Task, a: Activity): void {
    this.service.eliminarActividad(this.projectId, a.readableId).subscribe({
      next: () => {
        this.tareaAbierta.set(t.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  // --- Dedicación ---

  protected abrirHoras(a: Activity): void {
    this.registrandoHoras.set(a.readableId);
    this.horas = null;
    this.fechaHoras = new Date().toISOString().slice(0, 10);
    this.notaHoras = '';
    this.error.set(null);
  }

  protected guardarHoras(t: Task, a: Activity): void {
    if (!this.horas || this.horas <= 0) {
      this.error.set('Las horas dedicadas han de ser mayores que cero.');
      return;
    }
    this.guardando.set(true);

    this.service
      .registrarHoras(this.projectId, a.readableId, {
        hours: this.horas,
        workedOn: this.fechaHoras || undefined,
        note: this.notaHoras.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.registrandoHoras.set(null);
          this.tareaAbierta.set(t.readableId);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  // --- Recursos ---

  protected abrirRecurso(): void {
    this.creandoRecurso.set(true);
    this.nombreRecurso = '';
    this.claseRecurso = 'EQUIPMENT';
    this.unidadRecurso = '';
    this.cantidadRecurso = null;
    this.error.set(null);
  }

  protected guardarRecurso(): void {
    if (this.nombreRecurso.trim().length === 0) {
      this.error.set('El nombre del recurso es obligatorio.');
      return;
    }
    this.guardando.set(true);

    this.service
      .crearRecurso(this.projectId, {
        name: this.nombreRecurso.trim(),
        kind: this.claseRecurso,
        unit: this.unidadRecurso.trim() || undefined,
        quantity: this.cantidadRecurso ?? undefined,
      })
      .subscribe({
        next: (r) => {
          this.guardando.set(false);
          this.creandoRecurso.set(false);
          this.aviso.set(`Recurso ${r.readableId} creado.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected abrirEdicionRecurso(r: { readableId: string; name: string; kind: string;
      unit: string | null; quantity: number | null }): void {
    this.editandoRecurso.set(r.readableId);
    this.nombreRecurso = r.name;
    this.claseRecurso = r.kind;
    this.unidadRecurso = r.unit ?? '';
    this.cantidadRecurso = r.quantity;
    this.error.set(null);
  }

  protected actualizarRecurso(readableId: string): void {
    if (this.nombreRecurso.trim().length === 0) {
      this.error.set('El nombre del recurso es obligatorio.');
      return;
    }
    this.guardando.set(true);

    this.service
      .editarRecurso(this.projectId, readableId, {
        name: this.nombreRecurso.trim(),
        kind: this.claseRecurso,
        unit: this.unidadRecurso.trim() || undefined,
        quantity: this.cantidadRecurso ?? undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editandoRecurso.set(null);
          this.aviso.set(`Recurso ${readableId} actualizado.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected eliminarRecurso(readableId: string): void {
    this.service.eliminarRecurso(this.projectId, readableId).subscribe({
      next: () => {
        this.aviso.set(`Recurso ${readableId} eliminado.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  protected abrirAsignacion(t: Task): void {
    this.asignandoRecurso.set(t.readableId);
    this.recursoElegido = this.plan()?.resources[0]?.readableId ?? '';
    this.cantidadAsignada = null;
    this.error.set(null);
  }

  protected asignarRecurso(t: Task): void {
    if (!this.recursoElegido) {
      this.error.set('Elija un recurso.');
      return;
    }
    this.service
      .asignarRecurso(this.projectId, t.readableId, {
        resource: this.recursoElegido,
        quantity: this.cantidadAsignada ?? undefined,
      })
      .subscribe({
        next: () => {
          this.asignandoRecurso.set(null);
          this.tareaAbierta.set(t.readableId);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.fallo(fallo),
      });
  }

  protected retirarRecurso(t: Task, recurso: string): void {
    this.service.retirarRecurso(this.projectId, t.readableId, recurso).subscribe({
      next: () => {
        this.tareaAbierta.set(t.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.fallo(fallo),
    });
  }

  // --- Errores ---

  private fallo(fallo: HttpErrorResponse): void {
    this.guardando.set(false);
    this.error.set(this.explicar(fallo));
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
      return 'No tiene atribuciones para esta operación. Planificar corresponde al facilitador; ejecutar, al miembro del equipo.';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
