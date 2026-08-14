import { HttpErrorResponse } from '@angular/common/http';

import { conservarPosicion } from '../shared/desplazamiento';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { SessionService } from '../auth/session-service';
import { InviteResult, PendingInvite } from '../invitations/invitation';
import { InvitationService } from '../invitations/invitation-service';
import { ApiError } from '../registration/registration';
import { ALCANCE_ROL, ETIQUETA_ROL, Member, Project, ProjectRole } from './project';
import { ProjectService } from './project-service';

/**
 * Espacio de trabajo. Es la misma pantalla para todos los roles, y lo que
 * muestra depende de la membresía de cada uno en cada proyecto.
 *
 * Se eligió una pantalla por persona y no una por rol porque una misma persona
 * puede ser facilitadora en un proyecto y propietaria en otro: separar por rol
 * la obligaría a cambiar de pantalla para ver su propio trabajo.
 *
 * Lo que aquí se muestra u oculta es comodidad, nunca autorización: SEC-05
 * exige que toda restricción esté impuesta también en el servicio.
 */
@Component({
  selector: 'slcp-workspace-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './workspace-page.html',
  styleUrl: './workspace-page.css',
})
export class WorkspacePage implements OnInit {
  private readonly service = inject(ProjectService);
  private readonly invitaciones = inject(InvitationService);
  private readonly sesion = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly etiquetaRol = ETIQUETA_ROL;
  protected readonly alcanceRol = ALCANCE_ROL;

  protected readonly proyectos = signal<Project[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly usuario = this.sesion.sesion;
  protected readonly puedeCrear = computed(() => this.usuario()?.platformRole === 'FACILITATOR');

  /** Alta de proyecto. */
  protected readonly creando = signal(false);
  protected nombre = '';
  protected proposito = '';

  /** Proyecto en edición, y sus datos mientras se corrigen. */
  protected readonly editando = signal<string | null>(null);
  protected edicionNombre = '';
  protected edicionProposito = '';

  /** Proyecto cuya eliminación se está confirmando. */
  protected readonly confirmando = signal<string | null>(null);

  /** Miembro cuyo rol se está cambiando, y el rol elegido. */
  protected readonly cambiandoRol = signal<string | null>(null);

  /** Rol elegido al cambiar el de alguien. Es distinto del de invitar: son dos
   * formularios abiertos a la vez, y compartir el campo haría que tocar uno
   * cambiara el otro. */
  protected rolDeCambio: ProjectRole = 'TEAM_MEMBER';
  protected readonly retirando = signal<string | null>(null);

  /** Proyecto cuyo equipo se está consultando. */
  protected readonly abierto = signal<string | null>(null);
  protected readonly equipo = signal<Member[]>([]);
  protected readonly cargandoEquipo = signal(false);

  /** Alta de integrante: un solo formulario para los tres caminos. */
  protected identificador = '';
  protected rolNuevo: ProjectRole = 'TEAM_MEMBER';
  protected readonly incorporando = signal(false);

  /** Invitaciones vigentes del proyecto abierto. */
  protected readonly pendientesProyecto = signal<PendingInvite[]>([]);

  /**
   * Resultado de la última invitación, con su enlace.
   *
   * El enlace se muestra porque la plataforma todavía no envía correo. Cuando
   * lo envíe, dejará de mostrarse: quien invita no debe poder usar el enlace,
   * o podría crear una cuenta a nombre de una dirección ajena.
   */
  protected readonly ultimaInvitacion = signal<InviteResult | null>(null);
  protected readonly copiado = signal(false);

  /** Invitaciones dirigidas a quien está usando la aplicación. */
  protected readonly misInvitaciones = signal<PendingInvite[]>([]);
  protected readonly respondiendo = signal<string | null>(null);

  protected readonly rolesAsignables: ProjectRole[] = [
    'TEAM_MEMBER',
    'PRODUCT_OWNER',
    'PROJECT_FACILITATOR',
  ];

  ngOnInit(): void {
    this.cargar();
    this.cargarMisInvitaciones();
  }

  protected cargarMisInvitaciones(): void {
    this.invitaciones.mias().subscribe({
      next: (lista) => this.misInvitaciones.set(lista),
      error: () => this.misInvitaciones.set([]),
    });
  }

  protected aceptarInvitacion(inv: PendingInvite): void {
    this.respondiendo.set(inv.id);
    this.invitaciones.aceptar(inv.id).subscribe({
      next: (r) => {
        this.respondiendo.set(null);
        this.aviso.set(`${r.message} Proyecto ${r.projectName}, como ${r.roleLabel}.`);
        this.misInvitaciones.update((l) => l.filter((i) => i.id !== inv.id));
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.respondiendo.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected rechazarInvitacion(inv: PendingInvite): void {
    this.respondiendo.set(inv.id);
    this.invitaciones.rechazar(inv.id).subscribe({
      next: () => {
        this.respondiendo.set(null);
        this.aviso.set('Invitación rechazada.');
        this.misInvitaciones.update((l) => l.filter((i) => i.id !== inv.id));
      },
      error: (fallo: HttpErrorResponse) => {
        this.respondiendo.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  /**
   * Vuelve a pedir los proyectos.
   *
   * Conserva la posición: con varios proyectos, tras cambiar un rol o retirar a
   * alguien la página volvía arriba.
   */
  protected cargar(): void {
    const volver = conservarPosicion();
    this.cargando.set(true);
    this.service.mios().subscribe({
      next: (lista) => {
        this.proyectos.set(lista);
        this.cargando.set(false);
        volver();
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargando.set(false);
      },
    });
  }

  protected esFacilitadorDe(p: Project): boolean {
    return p.myRoles.includes('PROJECT_FACILITATOR');
  }

  protected crear(): void {
    if (this.nombre.trim().length === 0) {
      return;
    }
    this.creando.set(true);
    this.error.set(null);

    this.service.crear(this.nombre.trim(), this.proposito.trim()).subscribe({
      next: (p) => {
        this.creando.set(false);
        this.nombre = '';
        this.proposito = '';
        this.aviso.set(`Proyecto ${p.readableId} creado. Usted es su facilitador.`);
        this.proyectos.update((lista) => [p, ...lista]);
      },
      error: (fallo: HttpErrorResponse) => {
        this.creando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected editarProyecto(p: Project): void {
    this.editando.set(p.readableId);
    this.edicionNombre = p.name;
    this.edicionProposito = p.purpose ?? '';
    this.error.set(null);
  }

  protected cancelarEdicion(): void {
    this.editando.set(null);
  }

  protected guardarProyecto(p: Project): void {
    if (this.edicionNombre.trim().length === 0) {
      this.error.set('El nombre del proyecto es obligatorio.');
      return;
    }
    this.service.editar(p.readableId, this.edicionNombre.trim(), this.edicionProposito.trim())
      .subscribe({
        next: () => {
          this.editando.set(null);
          this.aviso.set(`Proyecto ${p.readableId} actualizado.`);
          this.cargar();
        },
        error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
      });
  }

  /** Retira el proyecto del servicio, o lo devuelve. El contenido permanece. */
  protected alternarServicio(p: Project): void {
    const activo = p.status !== 'ACTIVE';
    this.service.cambiarEstado(p.readableId, activo).subscribe({
      next: () => {
        this.aviso.set(activo
          ? `Proyecto ${p.readableId} devuelto al servicio.`
          : `Proyecto ${p.readableId} retirado del servicio. Su contenido permanece.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  protected pedirConfirmacion(p: Project): void {
    this.confirmando.set(p.readableId);
    this.error.set(null);
  }

  protected cancelarEliminacion(): void {
    this.confirmando.set(null);
  }

  protected eliminarProyecto(p: Project): void {
    this.service.eliminar(p.readableId).subscribe({
      next: () => {
        this.confirmando.set(null);
        this.aviso.set(`Proyecto ${p.readableId} eliminado.`);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.confirmando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected empezarCambioDeRol(m: Member): void {
    this.cambiandoRol.set(m.username);
    this.rolDeCambio = m.role;
    this.error.set(null);
  }

  protected cancelarCambioDeRol(): void {
    this.cambiandoRol.set(null);
  }

  protected guardarRol(p: Project, m: Member): void {
    this.service.cambiarRol(p.readableId, m.username, this.rolDeCambio).subscribe({
      next: (r) => {
        this.cambiandoRol.set(null);
        this.aviso.set(`${m.username} pasa a ${r.roleLabel}.`);
        this.recargarEquipo(p);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.cambiandoRol.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected pedirRetirada(m: Member): void {
    this.retirando.set(m.username);
    this.error.set(null);
  }

  protected cancelarRetirada(): void {
    this.retirando.set(null);
  }

  protected retirar(p: Project, m: Member): void {
    this.service.retirarDelEquipo(p.readableId, m.username).subscribe({
      next: () => {
        this.retirando.set(null);
        this.aviso.set(`${m.username} retirado del equipo. Su rastro se conserva.`);
        this.recargarEquipo(p);
        this.cargar();
      },
      error: (fallo: HttpErrorResponse) => {
        this.retirando.set(null);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected alternarEquipo(p: Project): void {
    if (this.abierto() === p.readableId) {
      this.abierto.set(null);
      return;
    }
    this.abierto.set(p.readableId);
    this.equipo.set([]);
    this.pendientesProyecto.set([]);
    this.ultimaInvitacion.set(null);
    this.cargandoEquipo.set(true);
    this.identificador = '';

    if (this.esFacilitadorDe(p)) {
      this.invitaciones.vigentes(p.readableId).subscribe({
        next: (lista) => this.pendientesProyecto.set(lista),
        error: () => this.pendientesProyecto.set([]),
      });
    }

    this.recargarEquipo(p);
  }

  /** Vuelve a pedir el equipo del proyecto. */
  private recargarEquipo(p: Project): void {
    this.cargandoEquipo.set(true);
    this.service.equipo(p.readableId).subscribe({
      next: (lista) => {
        this.equipo.set(lista);
        this.cargandoEquipo.set(false);
      },
      error: (fallo: HttpErrorResponse) => {
        this.error.set(this.explicar(fallo));
        this.cargandoEquipo.set(false);
      },
    });
  }

  /**
   * Incorpora a alguien al equipo.
   *
   * Un solo formulario para los tres caminos: quien invita indica siempre un
   * correo y un rol, y es el servicio quien decide si la persona se incorpora
   * de inmediato, debe aceptar, o debe completar antes su registro. Obligar a
   * elegir el camino de antemano trasladaría a quien invita una distinción que
   * no le corresponde conocer.
   */
  protected incorporar(p: Project): void {
    const valor = this.identificador.trim();
    if (valor.length === 0) {
      return;
    }
    this.incorporando.set(true);
    this.error.set(null);
    this.ultimaInvitacion.set(null);
    this.copiado.set(false);

    this.invitaciones.invitar(p.readableId, valor, this.rolNuevo).subscribe({
      next: (r) => {
        this.incorporando.set(false);
        this.identificador = '';
        this.ultimaInvitacion.set(r);
        this.aviso.set(r.message);
        if (r.camino === 'PENDIENTE_DE_REGISTRO' || r.camino === 'PENDIENTE_DE_ACEPTACION') {
          this.invitaciones.vigentes(p.readableId).subscribe({
            next: (lista) => this.pendientesProyecto.set(lista),
          });
        }
      },
      error: (fallo: HttpErrorResponse) => {
        this.incorporando.set(false);
        this.error.set(this.explicar(fallo));
      },
    });
  }

  protected revocarInvitacion(p: Project, inv: PendingInvite): void {
    this.invitaciones.revocar(p.readableId, inv.id).subscribe({
      next: () => {
        this.pendientesProyecto.update((l) => l.filter((i) => i.id !== inv.id));
        this.aviso.set(`Invitación a ${inv.email} retirada. El enlace deja de servir.`);
      },
      error: (fallo: HttpErrorResponse) => this.error.set(this.explicar(fallo)),
    });
  }

  /**
   * Copia el enlace, que solo existe cuando la entrega por correo falló.
   *
   * Si el correo salió, el enlace no llega a la interfaz: quien invita no debe
   * poder usarlo, o podría crear una cuenta a nombre de una dirección ajena.
   */
  protected copiar(r: InviteResult): void {
    if (!r.link) {
      return;
    }
    navigator.clipboard.writeText(r.link).then(
      () => this.copiado.set(true),
      () => this.copiado.set(false),
    );
  }

  protected salir(): void {
    this.sesion.salir().subscribe({
      next: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/']),
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
