import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RegistrationService } from './registration-service';

describe('RegistrationService', () => {
  let service: RegistrationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RegistrationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia la solicitud a la ruta acordada', () => {
    service
      .solicitar({ username: 'gguerrero', email: 'g@uteq.edu.ec', fullName: 'Gleiston' })
      .subscribe();

    const peticion = http.expectOne('/api/v1/registrations');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body.username).toBe('gguerrero');
    peticion.flush({
      readableId: 'USR-ACC-0001-v1',
      username: 'gguerrero',
      status: 'PENDING_APPROVAL',
      requestedAt: '2026-08-07T00:00:00Z',
      message: 'Pendiente de aprobacion',
    });
  });

  it('no envia el rol: no es elegible por quien solicita', () => {
    service
      .solicitar({ username: 'gguerrero', email: 'g@uteq.edu.ec', fullName: 'Gleiston' })
      .subscribe();

    const peticion = http.expectOne('/api/v1/registrations');
    expect(peticion.request.body.role).toBeUndefined();
    peticion.flush({});
  });
});
