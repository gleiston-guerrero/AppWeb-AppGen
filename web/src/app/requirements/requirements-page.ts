import { HttpErrorResponse } from '@angular/common/http';

import { volverAlElemento } from '../shared/desplazamiento';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { SessionService } from '../auth/session-service';
import { ProjectService } from '../projects/project-service';
import { ApiError } from '../registration/registration';
import {
  CheckResult,
  HeldGroup,
  HeldSuspect,
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
  protected readonly esFacilitador = signal(false);

  /** Identificador de quien está usando la aplicación, para la doble etapa. */
  protected readonly yo = computed(() => this.sesion.sesion()?.userId ?? '');

  /**
   * Filtro de la lista, como señal.
   *
   * Igual que el formato: `visibles` lo deriva con `computed`, y un valor
   * calculado solo se recalcula cuando cambia una señal. Con un campo normal,
   * los botones de filtro cambiaban de aspecto y la lista no se movía.
   */
  protected readonly filtro = signal<'TODOS' | 'CON_HALLAZGOS' | 'SIN_CRITERIO' | 'APROBADOS'>(
    'TODOS',
  );

  protected readonly visibles = computed(() => {
    const todos = this.requisitos();
    switch (this.filtro()) {
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
  /**
   * Formato elegido, como señal.
   *
   * Ha de serlo porque `formatoElegido` lo deriva con `computed`, y un valor
   * calculado solo se recalcula cuando cambia una señal. Con un campo normal, el
   * panel quedaba fijado en el primer formato y describía uno distinto del
   * seleccionado.
   */
  protected readonly perfil = signal('');

  /** Formatos admitidos y el elegido, con su ejemplo. */
  protected readonly formatos = signal<ImportProfile[]>([]);
  protected readonly formatoElegido = computed(
    () => this.formatos().find((f) => f.id === this.perfil()) ?? null,
  );

  /** Extensiones que acepta el selector de archivo, según el formato elegido. */
  protected readonly extensionesAceptadas = computed(() => {
    const f = this.formatoElegido();
    return f ? f.extensions.join(',') : '.tex,.md,.txt';
  });

  /**
   * Formulario de alta y de corrección.
   *
   * Es el mismo para ambas cosas a propósito: corregir un requisito es volver a
   * redactarlo, y llevar a quien revisa a otra pantalla le haría perder de vista
   * lo que estaba corrigiendo.
   */
  protected readonly redactando = signal(false);
  protected nuevoEnunciado = '';
  protected nuevoCriterio = '';

  /** Quién ejerce lo que el requisito describe. Puede quedar sin declarar. */
  protected nuevoActor = '';
  protected nuevoNombre = '';
  protected nuevoTipo = 'FUNCTIONAL';

  /** Requisito que se está corrigiendo, o nulo si se está creando uno nuevo. */
  protected readonly editando = signal<Requirement | null>(null);

  /** Opciones de redacción del requisito en corrección, y cuál se muestra. */
  protected readonly opciones = signal<Suggestion[]>([]);
  protected readonly opcionActual = signal(0);

  protected readonly opcionMostrada = computed(() => {
    const lista = this.opciones();
    const i = this.opcionActual();
    return i > 0 && i <= lista.length ? lista[i - 1] : null;
  });

  /** Si el texto del formulario procede de una propuesta y no se ha tocado. */
  protected readonly deSugerencia = signal(false);

  /** Requisito desplegado y edición en curso. */
  protected readonly abierto = signal<string | null>(null);
  protected readonly guardando = signal<string | null>(null);

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
        if (this.perfil().length === 0 && lista.length > 0) {
          this.perfil.set(lista[0].id);
        }
      },
      error: (fallo: HttpErrorResponse) => {
        // Un fallo no puede parecer "no hay nada": son cosas distintas y quien
        // mira no puede distinguirlas.
        this.formatos.set([]);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /**
   * Cambia de formato.
   *
   * Recibe el valor nuevo como argumento en lugar de leerlo del campo: con
   * `[(ngModel)]` y `(ngModelChange)` juntos, el manejador puede ejecutarse antes
   * de que el enlace haya escrito el valor, y entonces se actuaría sobre el
   * anterior.
   *
   * El archivo elegido se descarta: fue elegido para otro formato.
   */
  protected cambiarFormato(id: string): void {
    this.perfil.set(id);
    this.contenido = '';
    this.nombreArchivo = '';
    this.resultadoImport.set(null);
    this.error.set(null);
  }

  private cargarRoles(): void {
    this.proyectos.mios().subscribe({
      next: (lista) => {
        const p = lista.find((x) => x.readableId === this.projectId);
        this.esEquipo.set(p?.myRoles.includes('TEAM_MEMBER') ?? false);
        this.esPropietario.set(p?.myRoles.includes('PRODUCT_OWNER') ?? false);
        this.esFacilitador.set(p?.myRoles.includes('PROJECT_FACILITATOR') ?? false);
      },
      error: () => {
        this.esEquipo.set(false);
        this.esPropietario.set(false);
        this.esFacilitador.set(false);
      },
    });
  }

  protected cargar(despues?: () => void): void {
    this.cargando.set(true);
    this.service.listar(this.projectId).subscribe({
      next: (lista) => {
        this.requisitos.set(lista);
        this.cargando.set(false);
        if (despues) {
          // Se espera al siguiente ciclo: la lista acaba de cambiar y el elemento
          // al que hay que volver todavía no está en la página.
          setTimeout(despues, 0);
        }
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
    this.service.resumen(this.projectId).subscribe({
      next: (r) => this.resumen.set(r),
      error: (fallo: HttpErrorResponse) => {
        // Un fallo no puede parecer "no hay nada": son cosas distintas y quien
        // mira no puede distinguirlas.
        this.resumen.set(null);
        this.error.set(this.explicar(fallo));
      },
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

  /** Da de alta un grupo retenido. La decisión es de quien produce. */
  protected aceptarGrupo(g: HeldGroup, transversal: boolean): void {
    this.aceptandoGrupo.set(g.label);

    this.service.aceptarRetenidos(this.projectId, g.requirements).subscribe({
      next: (r) => {
        this.aceptandoGrupo.set(null);
        this.aviso.set(r.message);
        this.descartarGrupo(g, transversal);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.aceptandoGrupo.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /** Descarta el grupo sin darlo de alta: no se guardó nada, no hay que deshacer. */
  protected descartarGrupo(g: HeldGroup, transversal: boolean): void {
    const quitar = (lista: HeldGroup[]) => lista.filter((x) => x.label !== g.label);
    if (transversal) {
      this.transversales.set(quitar(this.transversales()));
    } else {
      this.ajenos.set(quitar(this.ajenos()));
    }
  }

  /** Da de alta un requisito que se parecía a otro, tras decidir que es distinto. */
  protected aceptarSospechoso(s: HeldSuspect): void {
    this.aceptandoGrupo.set(s.requirement.statement);

    this.service.aceptarRetenidos(this.projectId, [s.requirement]).subscribe({
      next: (r) => {
        this.aceptandoGrupo.set(null);
        this.aviso.set(r.message);
        this.descartarSospechoso(s);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.aceptandoGrupo.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /** Lo descarta por ser el mismo requisito que el que ya existe. */
  protected descartarSospechoso(s: HeldSuspect): void {
    this.sospechosos.set(
      this.sospechosos().filter((x) => x.requirement.statement !== s.requirement.statement),
    );
  }

  protected cuantosRetenidos(): number {
    const contar = (l: HeldGroup[]) => l.reduce((n, g) => n + g.requirements.length, 0);
    return contar(this.transversales()) + contar(this.ajenos()) + this.sospechosos().length;
  }

  protected importar(): void {
    if (this.contenido.trim().length === 0) {
      this.error.set('Elija un archivo o pegue el contenido antes de importar.');
      return;
    }
    this.importando.set(true);
    this.error.set(null);

    this.service.importar(this.projectId, this.perfil(), this.contenido).subscribe({
      next: (r) => {
        this.importando.set(false);
        this.resultadoImport.set(r);
        this.transversales.set(r.crossCutting);
        this.ajenos.set(r.foreign);
        this.sospechosos.set(r.suspected);
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

  /**
   * Lleva un requisito al formulario para corregirlo.
   *
   * Se cargan sus opciones de redacción, si las tiene, y se empieza mostrando el
   * texto original: quien revisa debe ver primero lo que hay y después lo que se
   * le propone, no al revés (ANA-14).
   */
  protected corregir(r: Requirement): void {
    this.editando.set(r);
    this.nuevoNombre = r.name ?? '';
    this.nuevoEnunciado = r.statement;
    this.nuevoCriterio = r.verification ?? '';
    this.nuevoActor = r.actor ?? '';
    this.nuevoTipo = r.kind;
    this.opciones.set(r.statementSuggestions);
    this.opcionActual.set(0);
    this.deSugerencia.set(false);
    this.error.set(null);
    this.aviso.set(null);

    document.getElementById('formulario-requisito')?.scrollIntoView({ behavior: 'smooth' });
  }

  /** Muestra una opción concreta. El índice cero es el texto original. */
  protected mostrarOpcion(indice: number): void {
    const lista = this.opciones();
    if (indice < 0 || indice > lista.length) {
      return;
    }
    this.opcionActual.set(indice);

    if (indice === 0) {
      this.nuevoEnunciado = this.editando()?.statement ?? '';
      this.deSugerencia.set(false);
    } else {
      this.nuevoEnunciado = lista[indice - 1].text;
      this.deSugerencia.set(true);
    }
  }

  protected opcionAnterior(): void {
    this.mostrarOpcion(Math.max(0, this.opcionActual() - 1));
  }

  protected opcionSiguiente(): void {
    this.mostrarOpcion(Math.min(this.opciones().length, this.opcionActual() + 1));
  }

  /** Al escribir sobre una propuesta, el texto deja de ser de la plataforma. */
  protected textoTocado(): void {
    const i = this.opcionActual();
    const lista = this.opciones();
    const propuesta = i > 0 && i <= lista.length ? lista[i - 1].text : null;
    this.deSugerencia.set(propuesta !== null && propuesta === this.nuevoEnunciado);
  }

  /**
   * Prepara el formulario para un requisito nuevo y lleva la vista hasta él.
   *
   * Se limpia lo que hubiera: si se venía de corregir otro, sus datos seguirían
   * en los campos y el alta saldría con ellos.
   */
  protected nuevo(): void {
    this.cancelarCorreccion();
    this.comprobacion.set(null);
    this.error.set(null);

    setTimeout(() => {
      document
        .getElementById('formulario-requisito')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      document.getElementById('enunciado-requisito')?.focus();
    }, 0);
  }

  protected cancelarCorreccion(): void {
    this.editando.set(null);
    this.opciones.set([]);
    this.opcionActual.set(0);
    this.deSugerencia.set(false);
    this.nuevoEnunciado = '';
    this.nuevoCriterio = '';
    this.nuevoActor = '';
    this.nuevoNombre = '';
  }

  /**
   * Crea o actualiza, comprobando antes si ya existe algo parecido.
   *
   * La comprobación se salta al modificar un requisito existente —se parecería a
   * sí mismo— y cuando quien redacta ya la ha visto y decide seguir.
   */
  protected guardar(forzar = false): void {
    if (this.nuevoEnunciado.trim().length === 0) {
      this.error.set('El enunciado es obligatorio.');
      return;
    }

    if (!forzar && !this.editando()) {
      this.service.comprobar(this.projectId, this.nuevoEnunciado.trim()).subscribe({
        next: (r) => {
          if (r.clean) {
            this.comprobacion.set(null);
            this.guardarDeVerdad();
          } else {
            this.comprobacion.set(r);
          }
        },
        // Si la comprobación falla, se crea igualmente: es una ayuda, no una
        // condición para trabajar.
        error: () => this.guardarDeVerdad(),
      });
      return;
    }

    this.comprobacion.set(null);
    this.guardarDeVerdad();
  }

  protected descartarComprobacion(): void {
    this.comprobacion.set(null);
  }

  private guardarDeVerdad(): void {
    this.redactando.set(true);
    this.error.set(null);

    const datos = {
      kind: this.nuevoTipo,
      name: this.nuevoNombre.trim() || undefined,
      statement: this.nuevoEnunciado.trim(),
      verification: this.nuevoCriterio.trim() || undefined,
      actor: this.nuevoActor.trim() || undefined,
    };

    const enCorreccion = this.editando();

    const peticion = enCorreccion
      ? this.service.editar(this.projectId, enCorreccion.readableId, datos, this.deSugerencia())
      : this.service.crear(this.projectId, datos);

    peticion.subscribe({
      next: (r) => {
        this.redactando.set(false);
        this.aviso.set(
          enCorreccion
            ? `Requisito ${r.readableId} actualizado.` +
              (this.deSugerencia() ? ' Queda registrado que su texto procede de una propuesta.' : '')
            : `Requisito ${r.readableId} creado en borrador.`,
        );
        this.cancelarCorreccion();
        this.comprobacion.set(null);

        // Se vuelve al requisito y se deja desplegado, tanto al corregir uno como
        // al crear otro: quien acaba de escribirlo suele querer seguir con él
        // —marcarlo como revisado, ver los hallazgos—, y devolver la vista al
        // principio de la lista le obliga a buscarlo cada vez.
        this.abierto.set(r.readableId);
        this.cargar(() => this.volverA(r.readableId));
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
  }

  /** Acepta una propuesta tal cual, marcando su procedencia (ANA-16). */
  protected aceptar(r: Requirement, s: Suggestion): void {
    this.aplicar(r, s.text, true);
  }

  /** Lleva la propuesta de criterio al formulario, para modificarla antes de aceptarla (ANA-21). */
  protected modificarEnFormulario(r: Requirement, s: Suggestion): void {
    this.corregir(r);
    this.nuevoCriterio = s.text;
  }

  private aplicar(r: Requirement, criterio: string, deSugerencia: boolean): void {
    this.guardando.set(r.readableId);
    this.error.set(null);

    this.service
      .editar(
        this.projectId,
        r.readableId,
        { statement: r.statement, verification: criterio, actor: r.actor ?? undefined },
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
          this.abierto.set(r.readableId);
          this.cargar(() => this.volverA(r.readableId));
        },
        error: (fallo: HttpErrorResponse) => {
          this.guardando.set(null);
          this.error.set(this.explicar(fallo));
        },
      });
  }

  /**
   * Cambia el estado y deja la pantalla donde estaba.
   *
   * Recargar la lista sustituye los elementos, y sin devolver la vista al
   * requisito recién decidido la página salta al principio: quien está
   * aprobando veinte requisitos seguidos tendría que buscar el siguiente cada
   * vez.
   */
  protected transitar(r: Requirement, destino: string): void {
    this.guardando.set(r.readableId);
    this.service.transitar(this.projectId, r.readableId, destino).subscribe({
      next: () => {
        this.guardando.set(null);
        this.aviso.set(`${r.readableId}: ${this.etiquetaEstado[destino] ?? destino}.`);
        this.abierto.set(r.readableId);
        this.cargar(() => this.volverA(r.readableId));
      },
      error: (fallo: HttpErrorResponse) => {
        this.guardando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /** Requisito cuya eliminación se está confirmando. */
  protected readonly confirmando = signal<string | null>(null);

  /**
   * Resultado de la comprobación previa al alta.
   *
   * Se comprueba antes de crear y no después porque quien escribe puede querer
   * corregir; avisar una vez creado obligaría a deshacer.
   */
  protected readonly comprobacion = signal<CheckResult | null>(null);

  /**
   * Grupos retenidos que aún esperan decisión.
   *
   * Se descartan de la lista al decidir sobre ellos, de modo que lo que queda a
   * la vista es siempre lo pendiente.
   */
  protected readonly transversales = signal<HeldGroup[]>([]);
  protected readonly ajenos = signal<HeldGroup[]>([]);
  protected readonly aceptandoGrupo = signal<string | null>(null);

  /** Retenidos por parecerse a otro: se muestran ambos enunciados. */
  protected readonly sospechosos = signal<HeldSuspect[]>([]);

  protected pedirConfirmacion(r: Requirement): void {
    this.confirmando.set(r.readableId);
    this.error.set(null);
  }

  protected cancelarEliminacion(): void {
    this.confirmando.set(null);
  }

  protected eliminar(r: Requirement): void {
    this.service.eliminar(this.projectId, r.readableId).subscribe({
      next: () => {
        this.confirmando.set(null);
        this.aviso.set(`Requisito ${r.readableId} eliminado.`);
        if (this.editando()?.readableId === r.readableId) {
          this.cancelarCorreccion();
        }
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.confirmando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /** Devuelve la vista al requisito indicado, sin sobresaltos. */
  /** Devuelve la vista al elemento indicado. */
  private readonly prefijo = 'req-';

  private volverA(readableId: string): void {
    volverAlElemento(this.prefijo + readableId);
  }

  /** Requisito siguiente pendiente de la misma decisión, para encadenarlas. */
  protected siguientePendiente(r: Requirement): Requirement | null {
    const lista = this.visibles();
    const desde = lista.findIndex((x) => x.readableId === r.readableId);

    for (let i = desde + 1; i < lista.length; i++) {
      if (lista[i].status === r.status) {
        return lista[i];
      }
    }
    return null;
  }

  /** Abre el siguiente pendiente y lleva la vista hasta él. */
  protected irAlSiguiente(r: Requirement): void {
    const siguiente = this.siguientePendiente(r);
    if (!siguiente) {
      return;
    }
    this.abierto.set(siguiente.readableId);
    setTimeout(() => this.volverA(siguiente.readableId), 0);
  }

  protected defectos(r: Requirement): number {
    return r.findings.filter((f) => f.severity === 'DEFECTO').length;
  }

  /**
   * Traduce el fallo a algo accionable.
   *
   * Se distinguen los códigos que tienen una causa concreta y frecuente: sin
   * ellos, un 403 aparece como «error 403» y quien lo ve no sabe que le falta un
   * rol, no un arreglo.
   */
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
      return 'No tiene atribuciones para esta operación en este proyecto. Editar requisitos corresponde al miembro del equipo, y aprobarlos al propietario del producto.';
    }
    return `El servicio devolvió un error ${fallo.status}.`;
  }
}
