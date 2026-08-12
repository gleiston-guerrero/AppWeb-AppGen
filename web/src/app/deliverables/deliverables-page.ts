import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProjectService } from '../projects/project-service';
import { RequirementService } from '../requirements/requirement-service';
import { RequirementSummary } from '../requirements/requirement';
import { ApiError } from '../registration/registration';
import { Deliverable, LinkableRequirement } from './deliverable';
import { DeliverableService } from './deliverable-service';

/**
 * Entregables de un proyecto: planificación del facilitador.
 *
 * El facilitador crea los entregables y los enlaza con los requisitos aprobados
 * que realizan. El propietario del producto los acepta, y esa aceptación es lo
 * que cierra los requisitos.
 */
@Component({
  selector: 'slcp-deliverables-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './deliverables-page.html',
  styleUrl: './deliverables-page.css',
})
export class DeliverablesPage implements OnInit {
  private readonly service = inject(DeliverableService);
  private readonly proyectos = inject(ProjectService);
  private readonly requisitos = inject(RequirementService);
  private readonly ruta = inject(ActivatedRoute);

  protected projectId = '';
  protected readonly entregables = signal<Deliverable[]>([]);
  protected readonly enlazables = signal<LinkableRequirement[]>([]);

  /**
   * Resumen de los requisitos del proyecto.
   *
   * Sirve para explicar una lista vacía. Sin él, quien no ve requisitos que
   * enlazar no sabe si es que no hay ninguno o si es que ninguno está aprobado,
   * y son dos situaciones con remedios distintos.
   */
  protected readonly resumenRequisitos = signal<RequirementSummary | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly esFacilitador = signal(false);
  protected readonly esEquipo = signal(false);
  protected readonly esPropietario = signal(false);

  /** Formulario de alta y de modificación: el mismo, como en requisitos. */
  protected readonly editando = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected nombre = '';
  protected descripcion = '';
  protected aceptacion = '';
  protected seleccionados = new Set<string>();

  protected readonly confirmando = signal<string | null>(null);
  protected readonly abierto = signal<string | null>(null);

  /** Cuántos requisitos del proyecto ya están cerrados. */
  protected readonly cerrados = computed(() => {
    const vistos = new Set<string>();
    for (const d of this.entregables()) {
      for (const r of d.requirements) {
        if (r.closed) {
          vistos.add(r.readableId);
        }
      }
    }
    return vistos.size;
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
        this.esPropietario.set(p?.myRoles.includes('PRODUCT_OWNER') ?? false);
      },
      error: () => {
        this.esFacilitador.set(false);
        this.esEquipo.set(false);
        this.esPropietario.set(false);
      },
    });
  }

  protected cargar(despues?: () => void): void {
    this.cargando.set(true);
    this.service.listar(this.projectId).subscribe({
      next: (lista) => {
        this.entregables.set(lista);
        this.cargando.set(false);
        if (despues) {
          // En el ciclo siguiente: la lista acaba de cambiar y el elemento al que
          // hay que volver todavía no está en la página.
          setTimeout(despues, 0);
        }
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
    this.cargarEnlazables();

    this.requisitos.resumen(this.projectId).subscribe({
      next: (r) => this.resumenRequisitos.set(r),
      error: () => this.resumenRequisitos.set(null),
    });
  }

  private cargarEnlazables(deliverable?: string): void {
    this.service.enlazables(this.projectId, deliverable).subscribe({
      next: (lista) => this.enlazables.set(lista),
      error: () => this.enlazables.set([]),
    });
  }

  // ---- Alta y modificación ----

  protected alternarRequisito(readableId: string): void {
    if (this.seleccionados.has(readableId)) {
      this.seleccionados.delete(readableId);
    } else {
      this.seleccionados.add(readableId);
    }
  }

  protected estaSeleccionado(readableId: string): boolean {
    return this.seleccionados.has(readableId);
  }

  protected modificar(d: Deliverable): void {
    this.editando.set(d.readableId);
    this.nombre = d.name;
    this.descripcion = d.description ?? '';
    this.aceptacion = d.acceptance ?? '';
    this.seleccionados = new Set(d.requirements.map((r) => r.readableId));
    this.error.set(null);
    this.cargarEnlazables(d.readableId);
    document.getElementById('formulario-entregable')?.scrollIntoView({ behavior: 'smooth' });
  }

  /**
   * Prepara el formulario para un entregable nuevo y lleva la vista hasta él.
   *
   * Se limpia lo que hubiera: si se venía de modificar otro, sus datos seguirían
   * en los campos y el alta saldría con ellos.
   */
  protected nuevo(): void {
    this.cancelar();
    this.error.set(null);
    this.cargarEnlazables();

    // En el ciclo siguiente: el formulario cambia de estado al limpiarlo, y
    // desplazarse antes llevaría a donde estaba, no a donde queda.
    setTimeout(() => {
      document
        .getElementById('formulario-entregable')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      document.getElementById('nombre-entregable')?.focus();
    }, 0);
  }

  protected cancelar(): void {
    this.editando.set(null);
    this.nombre = '';
    this.descripcion = '';
    this.aceptacion = '';
    this.seleccionados = new Set();
  }

  protected guardar(): void {
    if (this.nombre.trim().length === 0) {
      this.error.set('El nombre del entregable es obligatorio.');
      return;
    }
    if (this.seleccionados.size === 0) {
      this.error.set(
        'Enlace al menos un requisito aprobado. Un entregable que no realiza ningún requisito no puede cerrarse ni justificarse.',
      );
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const datos = {
      name: this.nombre.trim(),
      description: this.descripcion.trim() || undefined,
      acceptance: this.aceptacion.trim() || undefined,
      requirementIds: [...this.seleccionados],
    };

    const enEdicion = this.editando();
    const peticion = enEdicion
      ? this.service.editar(this.projectId, enEdicion, datos)
      : this.service.crear(this.projectId, datos);

    peticion.subscribe({
      next: (d) => {
        this.guardando.set(false);
        this.aviso.set(
          enEdicion ? `Entregable ${d.readableId} actualizado.` : `Entregable ${d.readableId} creado.`,
        );
        this.cancelar();
        this.abierto.set(d.readableId);
        this.cargar(() => this.volverA(d.readableId));
      },
      error: (fallo: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  // ---- Estado y borrado ----

  protected transitar(d: Deliverable, destino: string): void {
    this.service.transitar(this.projectId, d.readableId, destino).subscribe({
      next: (r) => {
        this.aviso.set(`${r.readableId}: ${r.statusLabel}.`);
        this.abierto.set(r.readableId);
        this.cargar(() => this.volverA(r.readableId));
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected pedirConfirmacion(d: Deliverable): void {
    this.confirmando.set(d.readableId);
    this.error.set(null);
  }

  protected cancelarEliminacion(): void {
    this.confirmando.set(null);
  }

  protected eliminar(d: Deliverable): void {
    this.service.eliminar(this.projectId, d.readableId).subscribe({
      next: () => {
        this.confirmando.set(null);
        this.aviso.set(`Entregable ${d.readableId} eliminado.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.confirmando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /** Devuelve la vista al entregable indicado. */
  private volverA(readableId: string): void {
    document
      .getElementById('ent-' + readableId)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  protected alternarDetalle(d: Deliverable): void {
    this.abierto.set(this.abierto() === d.readableId ? null : d.readableId);
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
      return 'No tiene atribuciones para esta operación. Planificar corresponde al facilitador, y aceptar al propietario del producto.';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
