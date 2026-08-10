import { HttpErrorResponse } from '@angular/common/http';
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

  protected cargar(): void {
    this.cargando.set(true);
    this.service.mios().subscribe({
      next: (lista) => {
        this.proyectos.set(lista);
        this.cargando.set(false);
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
