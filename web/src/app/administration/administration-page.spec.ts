import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AdministrationPage } from './administration-page';
import { PendingRegistration } from './administration';

describe('AdministrationPage', () => {
  let http: HttpTestingController;

  const pendiente: PendingRegistration = {
    readableId: 'USR-ACC-0002-v1',
    username: 'gguerrero1971',
    email: 'g@uteq.edu.ec',
    fullName: 'Gleiston Ciceron',
    requestedAt: '2026-08-08T10:00:00Z',
  };

  const URL = '/api/v1/administration/registrations/pending';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdministrationPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  it('FUN-16: lista las solicitudes a la espera', async () => {
    const fixture = TestBed.createComponent(AdministrationPage);
    fixture.detectChanges();
    http.expectOne(URL).flush([pendiente]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Gleiston Ciceron');
    expect(texto).toContain('USR-ACC-0002-v1');
  });

  it('sin solicitudes, lo dice en lugar de mostrar una lista vacia', async () => {
    const fixture = TestBed.createComponent(AdministrationPage);
    fixture.detectChanges();
    http.expectOne(URL).flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('No hay solicitudes');
  });

  it('ROL-05: el rechazo sin motivo no se envia', async () => {
    const fixture = TestBed.createComponent(AdministrationPage);
    fixture.detectChanges();
    http.expectOne(URL).flush([pendiente]);
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      abrirRechazo: (s: PendingRegistration) => void;
      confirmarRechazo: (s: PendingRegistration) => void;
      motivo: string;
    };

    c.abrirRechazo(pendiente);
    c.motivo = '   ';
    c.confirmarRechazo(pendiente);

    http.expectNone(`/api/v1/administration/registrations/${pendiente.readableId}/approval`);
  });

  it('aprobar retira la solicitud de la lista', async () => {
    const fixture = TestBed.createComponent(AdministrationPage);
    fixture.detectChanges();
    http.expectOne(URL).flush([pendiente]);
    await fixture.whenStable();

    const c = fixture.componentInstance as unknown as {
      aprobar: (s: PendingRegistration) => void;
      pendientes: () => PendingRegistration[];
    };

    c.aprobar(pendiente);

    const peticion = http.expectOne(
      `/api/v1/administration/registrations/${pendiente.readableId}/approval`,
    );
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body.approved).toBe(true);

    peticion.flush({
      readableId: pendiente.readableId,
      username: pendiente.username,
      status: 'ACTIVE',
      decidedAt: '2026-08-08T11:00:00Z',
      message: 'Solicitud aprobada.',
    });
    await fixture.whenStable();

    expect(c.pendientes()).toHaveLength(0);
  });
});
