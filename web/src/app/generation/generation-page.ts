import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProjectService } from '../projects/project-service';
import { conservarPosicion } from '../shared/desplazamiento';
import { ApiError } from '../registration/registration';
import {
  AiSettings,
  Artifact,
  EXPLICACION_CLASE,
  ETIQUETA_CLASE,
  GenerationState,
  Provider,
} from './generation';
import { DiagramView } from './diagram-view';
import { GenerationService } from './generation-service';

/**
 * Generación de pruebas y diagramas a partir de los requisitos aprobados.
 *
 * Las dos cosas son independientes y pueden pedirse en cualquier orden: quien
 * quiera solo diagramas no tiene que generar pruebas antes.
 *
 * Todo lo generado nace propuesto. Aceptarlo es un acto de una persona, y de ahí
 * sale la cobertura: un requisito está cubierto cuando alguien aceptó una prueba
 * suya, no cuando la plataforma la escribió.
 */
@Component({
  selector: 'slcp-generation-page',
  imports: [DiagramView, FormsModule, RouterLink],
  templateUrl: './generation-page.html',
  styleUrl: './generation-page.css',
})
export class GenerationPage implements OnInit {
  private readonly service = inject(GenerationService);
  private readonly proyectos = inject(ProjectService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly ETIQUETA = ETIQUETA_CLASE;
  protected readonly EXPLICACION = EXPLICACION_CLASE;

  protected projectId = '';
  protected readonly estado = signal<GenerationState | null>(null);
  protected readonly cargando = signal(true);
  protected readonly generando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly esEquipo = signal(false);
  protected readonly esFacilitador = signal(false);
  protected readonly esPropietario = signal(false);


  /** Qué está desplegado, y qué se está editando. */
  protected readonly abierto = signal<string | null>(null);
  protected readonly editando = signal<string | null>(null);
  protected contenidoEditado = '';

  /** Clases elegidas para generar. Vacío significa todas. */
  protected clasesDePrueba = new Set<string>();
  protected clasesDeDiagrama = new Set<string>();

  /** Qué pestaña se muestra: pruebas o diagramas. */
  protected readonly vista = signal<'TEST' | 'DIAGRAM'>('TEST');

  /**
   * Cómo se agrupa lo generado.
   *
   * Son dos preguntas distintas y ninguna sustituye a la otra. Por completitud
   * se responde «qué puedo ejecutar ya»; por tipo, «qué clase de comprobación
   * tengo cubierta». Mostrarlas juntas en una lista plana obliga a contar a ojo.
   */
  protected readonly agrupacion = signal<'COMPLETITUD' | 'TIPO'>('COMPLETITUD');

  /**
   * Cómo se genera: derivado del texto o asistido por un modelo.
   *
   * Lo elige quien genera. Lo derivado es reproducible y no sale del proyecto;
   * lo asistido identifica lo que el enunciado no dice, a cambio de enviar el
   * requisito a un tercero.
   */
  protected readonly modo = signal<'DERIVED' | 'ASSISTED'>('DERIVED');

  /**
   * Si el modo lo eligió la persona.
   *
   * Mientras no lo haya hecho, se propone el asistido si hay proveedor: da mejor
   * resultado y es lo que se quiere casi siempre. En cuanto elige, se respeta su
   * elección aunque se recargue.
   */
  private readonly modoElegido = signal(false);

  /** Requisitos aprobados sin ninguna prueba aceptada. */
  protected readonly sinCubrir = computed(
    () => this.estado()?.coverage.filter((c) => !c.covered) ?? [],
  );

  protected readonly cubiertos = computed(
    () => this.estado()?.coverage.filter((c) => c.covered).length ?? 0,
  );

  /** Artefactos de la pestaña activa. */
  protected readonly visibles = computed(() => {
    const e = this.estado();
    if (!e) {
      return [];
    }
    return this.vista() === 'TEST' ? e.tests : e.diagrams;
  });

  /** Los que esperan decisión: es lo que hay que mirar. */
  protected readonly pendientes = computed(
    () => this.visibles().filter((a) => a.status === 'PROPOSED').length,
  );

  /** Listas para ejecutar: sin huecos que rellenar. */
  protected readonly listas = computed(
    () => this.visibles().filter((a) => !a.needsDecision),
  );

  /** Exigen que alguien decida un valor antes de poder ejecutarse. */
  protected readonly conHuecos = computed(
    () => this.visibles().filter((a) => a.needsDecision),
  );

  /**
   * Lo visible, agrupado según el eje elegido.
   *
   * Se devuelven también los grupos vacíos por completitud —«listas» y «exigen
   * valores»— porque su ausencia informa: cero listas para ejecutar es un dato
   * que conviene ver, no una sección que desaparece.
   */
  protected readonly grupos = computed<{ clave: string; titulo: string; nota: string; items: Artifact[] }[]>(
    () => {
      if (this.agrupacion() === 'COMPLETITUD') {
        return [
          {
            clave: 'listas',
            titulo: 'Listas para ejecutar',
            nota: 'No les falta ningún valor. Pueden aceptarse y ejecutarse tal como están.',
            items: this.listas(),
          },
          {
            clave: 'huecos',
            titulo: 'Exigen especificar valores',
            nota: 'Traen huecos donde el requisito no dice lo suficiente. Complételos con Modificar antes de aceptarlas.',
            items: this.conHuecos(),
          },
        ];
      }

      // Por tipo: se agrupan por la clase que produjo cada artefacto.
      const porTipo = new Map<string, Artifact[]>();
      for (const a of this.visibles()) {
        const lista = porTipo.get(a.subkind) ?? [];
        lista.push(a);
        porTipo.set(a.subkind, lista);
      }

      return [...porTipo.entries()].map(([clave, items]) => ({
        clave,
        titulo: items[0].subkindLabel,
        nota: this.EXPLICACION[clave] ?? '',
        items,
      }));
    },
  );

  /** Cuántas de un grupo están listas: el dato que resume su estado. */
  protected listasDe(items: Artifact[]): number {
    return items.filter((a) => !a.needsDecision).length;
  }

  ngOnInit(): void {
    this.projectId = this.ruta.snapshot.paramMap.get('projectId') ?? '';

    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esEquipo.set(p?.myRoles.includes('TEAM_MEMBER') ?? false);
        this.esFacilitador.set(p?.myRoles.includes('PROJECT_FACILITATOR') ?? false);
        this.esPropietario.set(p?.myRoles.includes('PRODUCT_OWNER') ?? false);

      },
      error: () => {
        this.esEquipo.set(false);
        this.esFacilitador.set(false);
        this.esPropietario.set(false);
      },
    });

    this.cargar();
  }

  /**
   * Vuelve a pedir lo generado.
   *
   * Conserva la posición: tras aceptar o descartar un artefacto, la lista se
   * redibuja y sin esto habría que buscar otra vez dónde se estaba.
   */
  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);
    this.service.estado(this.projectId).subscribe({
      next: (e) => {
        this.estado.set(e);
        this.cargando.set(false);
        volver();

        if (!this.modoElegido()) {
          this.modo.set(e.assisted ? 'ASSISTED' : 'DERIVED');
        }
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  // --- Selección de clases ---

  /** La persona elige el modo, y a partir de ahí manda su elección. */
  protected elegirModo(modo: 'DERIVED' | 'ASSISTED'): void {
    this.modo.set(modo);
    this.modoElegido.set(true);
  }

  protected alternarClase(clase: string, deDiagrama: boolean): void {
    const conjunto = deDiagrama ? this.clasesDeDiagrama : this.clasesDePrueba;
    if (conjunto.has(clase)) {
      conjunto.delete(clase);
    } else {
      conjunto.add(clase);
    }
  }

  protected elegida(clase: string, deDiagrama: boolean): boolean {
    return (deDiagrama ? this.clasesDeDiagrama : this.clasesDePrueba).has(clase);
  }

  // --- Generación ---

  protected generar(kind: 'TEST' | 'DIAGRAM'): void {
    this.generando.set(true);
    this.error.set(null);

    const clases = kind === 'TEST' ? this.clasesDePrueba : this.clasesDeDiagrama;

    this.service
      .generar(this.projectId, {
        kind,
        subkinds: [...clases],
        requirements: [],
        mode: this.modo(),
      })
      .subscribe({
        next: (nuevos) => {
          this.generando.set(false);
          this.vista.set(kind);
          this.aviso.set(
            nuevos.length === 0
              ? 'No se generó nada: no hay requisitos que cumplan las condiciones de las clases elegidas.'
              : `${nuevos.length} propuestas generadas. Léalas antes de aceptarlas: son propuestas, no artefactos comprobados.`,
          );
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => {
          this.generando.set(false);
          this.error.set(this.explicar(fallo));
        },
      });
  }

  // --- Decisión ---

  protected alternar(a: Artifact): void {
    this.abierto.set(this.abierto() === a.readableId ? null : a.readableId);
  }

  protected decidir(a: Artifact, aceptar: boolean): void {
    this.service.decidir(this.projectId, a.readableId, aceptar).subscribe({
      next: (r) => {
        this.aviso.set(
          aceptar ? `${r.readableId} aceptado.` : `${r.readableId} descartado.`,
        );
        this.abierto.set(a.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  /**
   * El propietario del producto lo da por revisado.
   *
   * No es aprobarlo: eso lo hace el equipo, que es quien va a ejecutarlo. Puede
   * hacerlo antes o después de esa aprobación.
   */
  protected darPorRevisado(a: Artifact, revisado: boolean): void {
    this.service.darPorRevisado(this.projectId, a.readableId, revisado).subscribe({
      next: (r) => {
        this.aviso.set(
          revisado
            ? `${r.readableId} dado por revisado. El equipo sigue siendo quien lo aprueba.`
            : `Revisión retirada de ${r.readableId}.`,
        );
        this.abierto.set(a.readableId);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected abrirEdicion(a: Artifact): void {
    this.editando.set(a.readableId);
    this.contenidoEditado = a.content;
    this.abierto.set(a.readableId);
    this.error.set(null);
  }

  protected guardarEdicion(a: Artifact): void {
    if (this.contenidoEditado.trim().length === 0) {
      this.error.set('El contenido no puede quedar vacío.');
      return;
    }

    this.service
      .editar(this.projectId, a.readableId, { content: this.contenidoEditado })
      .subscribe({
        next: () => {
          this.editando.set(null);
          this.aviso.set(
            `${a.readableId} actualizado. Al modificarlo pasa a constar como escrito por una persona, y vuelve a propuesto.`,
          );
          this.abierto.set(a.readableId);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
      });
  }

  protected eliminar(a: Artifact): void {
    this.service.eliminar(this.projectId, a.readableId).subscribe({
      next: () => {
        this.aviso.set(`${a.readableId} eliminado.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  // --- Diagramas ---

  /** Si se muestra el texto del diagrama en lugar del dibujo. */
  protected readonly viendoTexto = signal<string | null>(null);

  protected alternarTexto(a: Artifact): void {
    this.viendoTexto.set(this.viendoTexto() === a.readableId ? null : a.readableId);
  }

  protected copiar(a: Artifact): void {
    navigator.clipboard.writeText(a.content).then(
      () => this.aviso.set(`Contenido de ${a.readableId} copiado.`),
      () => this.error.set('No se pudo copiar. Selecciónelo y cópielo a mano.'),
    );
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
      return 'Generar y decidir sobre pruebas y diagramas corresponde al miembro del equipo.';
    }
    if (this.modo() === 'ASSISTED' && !this.estado()?.assisted) {
      return 'Pidió generación asistida y este proyecto no tiene un servicio de IA activo. Configúrelo arriba, o cambie a «Sin IA».';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
