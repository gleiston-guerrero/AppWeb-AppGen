import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { ImportProfile, Requirement } from './requirement';
import { RequirementsPage } from './requirements-page';

/**
 * Estas pruebas vigilan un defecto concreto: derivar con `computed` un valor que
 * lee un campo que no es señal. El calculo no se rehace, y el sintoma es que la
 * pantalla no responde aunque el manejador se ejecute.
 */
describe('RequirementsPage', () => {
  let http: HttpTestingController;

  const base = {
    sourceId: null,
    sourceLine: null,
    kindLabel: 'Requisito funcional',
    name: null,
    verification: 'Se comprueba.',
    version: 1,
    reviewedBy: null,
    statementOrigin: 'HUMAN' as const,
    verificationOrigin: 'HUMAN' as const,
    conforming: true,
    deletable: true,
    findings: [],
    statementSuggestions: [],
    suggestions: [],
    updatedAt: '2026-08-12T10:00:00Z',
  };

  const requisitos: Requirement[] = [
    { ...base, readableId: 'REQ-0001-v1', kind: 'FUNCTIONAL', statement: 'Uno.', status: 'DRAFT' },
    {
      ...base,
      readableId: 'REQ-0002-v1',
      kind: 'FUNCTIONAL',
      statement: 'Dos.',
      status: 'APPROVED',
      verification: null,
      conforming: false,
      findings: [
        {
          rule: 'termino-sin-magnitud',
          characteristic: 'Verificable',
          severity: 'DEFECTO' as const,
          evidence: 'rapido',
          explanation: 'Sin magnitud.',
        },
      ],
    },
  ];

  const formatos: ImportProfile[] = [
    {
      id: 'texto-etiquetado',
      name: 'Texto plano con bloques separados',
      description: 'Archivo de texto.',
      extensions: ['.txt'],
      fields: ['id'],
      expected: ['id'],
      example: '[RF-01]',
    },
    {
      id: 'json-requisitos',
      name: 'JSON con lista de requisitos',
      description: 'Archivo JSON.',
      extensions: ['.json'],
      fields: ['id'],
      expected: ['id'],
      example: '{ }',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RequirementsPage],
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
    const fixture = TestBed.createComponent(RequirementsPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/projects').flush([
      { readableId: 'PRJ-0001-v1', name: 'Granja', purpose: '', status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z', myRoles: ['TEAM_MEMBER'], teamSize: 1 },
    ]);
    http.expectOne('/api/v1/projects/PRJ-0001-v1/requirements').flush(requisitos);
    http.expectOne('/api/v1/projects/PRJ-0001-v1/requirements/summary').flush({
      total: 2, conforming: 1, withFindings: 1, withoutCriterion: 1, approved: 1, suggestedText: 0,
    });
    http.expectOne('/api/v1/import-profiles').flush(formatos);
    return fixture;
  }

  it('al cambiar de formato, el panel describe el formato elegido', async () => {
    const fixture = iniciar();
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      cambiarFormato: (id: string) => void;
      formatoElegido: () => ImportProfile | null;
    };

    expect(c.formatoElegido()?.id).toBe('texto-etiquetado');

    c.cambiarFormato('json-requisitos');

    // El defecto que esta prueba vigila: con un campo normal en lugar de una
    // señal, esto seguia devolviendo el formato anterior.
    expect(c.formatoElegido()?.id).toBe('json-requisitos');
    expect(c.formatoElegido()?.name).toContain('JSON');
  });

  it('cambiar de formato descarta el archivo elegido para el anterior', async () => {
    const fixture = iniciar();
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      cambiarFormato: (id: string) => void;
      nombreArchivo: string;
      contenido: string;
    };

    c.nombreArchivo = 'granja.txt';
    c.contenido = '[RF-01]';
    c.cambiarFormato('json-requisitos');

    expect(c.nombreArchivo).toBe('');
    expect(c.contenido).toBe('');
  });

  it('tras actualizar, el requisito queda desplegado', async () => {
    const fixture = iniciar();
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      corregir: (r: Requirement) => void;
      guardar: (forzar?: boolean) => void;
      abierto: () => string | null;
    };

    c.corregir(requisitos[0]);
    c.guardar(true);

    http.expectOne('/api/v1/projects/PRJ-0001-v1/requirements/REQ-0001-v1')
        .flush({ ...requisitos[0], statement: 'Uno corregido.' });

    // Recarga posterior
    http.expectOne('/api/v1/projects/PRJ-0001-v1/requirements').flush(requisitos);
    http.expectOne('/api/v1/projects/PRJ-0001-v1/requirements/summary').flush({
      total: 2, conforming: 1, withFindings: 1, withoutCriterion: 1, approved: 1, suggestedText: 0,
    });
    http.expectOne('/api/v1/import-profiles').flush(formatos);

    // El defecto que esta prueba vigila: la lista se recargaba y la pagina volvia
    // al principio, dejando cerrado el requisito que se acababa de corregir.
    expect(c.abierto()).toBe('REQ-0001-v1');
  });

  it('el filtro cambia lo que se muestra', async () => {
    const fixture = iniciar();
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      filtro: { set: (v: string) => void };
      visibles: () => Requirement[];
    };

    expect(c.visibles()).toHaveLength(2);

    c.filtro.set('SIN_CRITERIO');
    expect(c.visibles()).toHaveLength(1);
    expect(c.visibles()[0].readableId).toBe('REQ-0002-v1');

    c.filtro.set('CON_HALLAZGOS');
    expect(c.visibles()).toHaveLength(1);

    c.filtro.set('APROBADOS');
    expect(c.visibles()[0].status).toBe('APPROVED');

    c.filtro.set('TODOS');
    expect(c.visibles()).toHaveLength(2);
  });
});
