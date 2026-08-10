import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SessionService } from '../auth/session-service';
import { Project } from './project';
import { WorkspacePage } from './workspace-page';

describe('WorkspacePage', () => {
  let http: HttpTestingController;

  const proyecto: Project = {
    readableId: 'PRJ-0001-v1',
    name: 'MundiPets',
    purpose: 'Gestion de mascotas',
    status: 'ACTIVE',
    createdAt: '2026-08-08T10:00:00Z',
    myRoles: ['PROJECT_FACILITATOR'],
    teamSize: 1,
  };

  function iniciar(rol: 'FACILITATOR' | 'MEMBER') {
    const sesion = TestBed.inject(SessionService);
    (sesion as unknown as { estado: { set: (v: unknown) => void } }).estado.set({
      userId: 'u1',
      readableId: 'USR-ACC-0001-v1',
      username: 'gguerrero',
      fullName: 'Gleiston Guerrero',
      platformRole: rol,
      mustChangePassword: false,
      expiresAt: '2099-01-01T00:00:00Z',
    });
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkspacePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  it('muestra los proyectos y el rol propio en cada uno', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();

    http.expectOne('/api/v1/projects').flush([proyecto]);
    http.expectOne('/api/v1/my-invitations').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('MundiPets');
    expect(texto).toContain('Facilitador');
  });

  it('quien no participa en nada recibe una explicacion, no una pantalla vacia', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();

    http.expectOne('/api/v1/projects').flush([]);
    http.expectOne('/api/v1/my-invitations').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Todav');
  });

  it('la incorporacion viaja con el rol que fija quien invita', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    http.expectOne('/api/v1/projects').flush([proyecto]);
    http.expectOne('/api/v1/my-invitations').flush([]);
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      identificador: string;
      rolNuevo: string;
      incorporar: (p: Project) => void;
    };

    c.identificador = 'nuevo@uteq.edu.ec';
    c.rolNuevo = 'PRODUCT_OWNER';
    c.incorporar(proyecto);

    const peticion = http.expectOne(`/api/v1/projects/${proyecto.readableId}/invitations`);
    expect(peticion.request.body.role).toBe('PRODUCT_OWNER');
    expect(peticion.request.body.email).toBe('nuevo@uteq.edu.ec');
    peticion.flush({ camino: 'PENDIENTE_DE_REGISTRO', link: null, message: 'ok' });
  });

  it('no invita con el identificador vacio', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    http.expectOne('/api/v1/projects').flush([proyecto]);
    http.expectOne('/api/v1/my-invitations').flush([]);
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      identificador: string;
      incorporar: (p: Project) => void;
    };
    c.identificador = '   ';
    c.incorporar(proyecto);

    http.expectNone(`/api/v1/projects/${proyecto.readableId}/invitations`);
  });
});
