import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { DeliverablesPage } from './deliverables-page';

describe('DeliverablesPage', () => {
  let http: HttpTestingController;

  const ENTREGABLE = {
    readableId: 'ENT-0007-v1',
    name: 'Acceso y trazabilidad',
    description: 'Seguridad del acceso.',
    acceptance: 'Iniciar sesion desde un dispositivo nuevo.',
    status: 'PLANNED' as const,
    statusLabel: 'Planificado',
    version: 1,
    deletable: true,
    acceptedBy: null,
    acceptedAt: null,
    requirements: [],
    updatedAt: '2026-08-12T10:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliverablesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'PRJ-0001-v1' } } },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  function iniciar() {
    const fixture = TestBed.createComponent(DeliverablesPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/projects').flush([
      { readableId: 'PRJ-0001-v1', name: 'Granja', purpose: '', status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        myRoles: ['PROJECT_FACILITATOR', 'TEAM_MEMBER'], teamSize: 1 },
    ]);
    http.expectOne('/api/v1/projects/PRJ-0001-v1/deliverables').flush([ENTREGABLE]);
    http.expectOne('/api/v1/projects/PRJ-0001-v1/deliverables/linkable-requirements').flush([]);
    http.expectOne('/api/v1/projects/PRJ-0001-v1/requirements/summary').flush({
      total: 2, conforming: 2, withFindings: 0, withoutCriterion: 0, approved: 2, suggestedText: 0,
    });
    return fixture;
  }

  afterEach(() => http.verify());

  it('«Nuevo» limpia lo que hubiera del entregable en modificacion', async () => {
    const fixture = iniciar();
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      modificar: (d: typeof ENTREGABLE) => void;
      nuevo: () => void;
      editando: () => string | null;
      nombre: string;
      seleccionados: Set<string>;
    };

    c.modificar(ENTREGABLE);
    expect(c.editando()).toBe('ENT-0007-v1');
    expect(c.nombre).toBe('Acceso y trazabilidad');

    c.nuevo();

    // El defecto que esta prueba vigila: sin limpiar, el alta saldria con los
    // datos del entregable que se estaba modificando.
    expect(c.editando()).toBeNull();
    expect(c.nombre).toBe('');
    expect(c.seleccionados.size).toBe(0);

    // «Nuevo» vuelve a pedir los requisitos enlazables, ya sin filtrar por uno
    http.match('/api/v1/projects/PRJ-0001-v1/deliverables/linkable-requirements')
        .forEach((p) => p.flush([]));
  });
});
