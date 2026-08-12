import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { sessionRefreshInterceptor } from './session-refresh';

describe('sessionRefreshInterceptor', () => {
  let http: HttpTestingController;
  let cliente: HttpClient;

  const SESION = {
    userId: 'u1',
    readableId: 'USR-ACC-0001-v1',
    username: 'gguerrero',
    fullName: 'Gleiston',
    platformRole: 'FACILITATOR',
    mustChangePassword: false,
    expiresAt: '2099-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionRefreshInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('ante una sesion caducada, renueva y repite la peticion', () => {
    let recibido: unknown = null;
    cliente.get('/api/v1/projects').subscribe((r) => (recibido = r));

    // La sesion ha caducado
    http.expectOne('/api/v1/projects').flush(null, { status: 401, statusText: 'Unauthorized' });

    // Se renueva
    const renovacion = http.expectOne('/api/v1/auth/sessions/current');
    expect(renovacion.request.method).toBe('PUT');
    renovacion.flush(SESION);

    // Y la peticion original se repite sin que nadie vuelva a pulsar
    http.expectOne('/api/v1/projects').flush([{ readableId: 'PRJ-0001-v1' }]);

    expect(recibido).toBeTruthy();
  });

  it('si la renovacion tambien falla, el error llega a quien pidio', () => {
    let fallo: unknown = null;
    cliente.get('/api/v1/projects').subscribe({ error: (e) => (fallo = e) });

    http.expectOne('/api/v1/projects').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/sessions/current')
        .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(fallo).toBeTruthy();
  });

  it('el propio inicio de sesion no intenta renovarse: seria un bucle', () => {
    let fallo: unknown = null;
    cliente.post('/api/v1/auth/sessions', {}).subscribe({ error: (e) => (fallo = e) });

    http.expectOne('/api/v1/auth/sessions').flush(
      { code: 'BAD_PASSWORD', message: 'La contrasena no es correcta', fields: {} },
      { status: 401, statusText: 'Unauthorized' },
    );

    // No debe haber intento de renovacion
    http.expectNone('/api/v1/auth/sessions/current');
    expect(fallo).toBeTruthy();
  });

  it('un error que no es de caducidad no provoca renovacion', () => {
    let fallo: unknown = null;
    cliente.get('/api/v1/projects').subscribe({ error: (e) => (fallo = e) });

    http.expectOne('/api/v1/projects').flush(null, { status: 403, statusText: 'Forbidden' });

    http.expectNone('/api/v1/auth/sessions/current');
    expect(fallo).toBeTruthy();
  });
});
