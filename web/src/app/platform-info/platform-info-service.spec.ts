import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PlatformInfo } from './platform-info';
import { PlatformInfoService } from './platform-info-service';

/**
 * Oraculo del servicio de acceso.
 *
 * Se escribe antes que la pantalla, conforme a VER-01, y no requiere que el
 * servicio HTTP este en marcha: la respuesta se simula.
 */
describe('PlatformInfoService', () => {
  let service: PlatformInfoService;
  let http: HttpTestingController;

  const ejemplo: PlatformInfo = {
    name: 'SLCP',
    version: '0.1.0-TEST',
    purpose: 'Genera aplicaciones web a partir de requisitos',
    input: 'La especificacion de requisitos del software',
    produces: ['Esquema relacional', 'Codigo fuente'],
    builtWith: ['Java 21', 'Angular'],
    authorship: 'Universidad Tecnica Estatal de Quevedo',
    license: 'MIT',
    repository: 'https://github.com/gleiston-guerrero/AppWeb-AppGen',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlatformInfoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('consulta la ruta acordada con el servicio', () => {
    service.load().subscribe();

    const peticion = http.expectOne('/api/v1/platform-info');
    expect(peticion.request.method).toBe('GET');
    peticion.flush(ejemplo);
  });

  it('devuelve la informacion recibida sin alterarla', () => {
    let recibido: PlatformInfo | undefined;
    service.load().subscribe((info) => (recibido = info));

    http.expectOne('/api/v1/platform-info').flush(ejemplo);

    expect(recibido).toEqual(ejemplo);
  });

  it('propaga el error cuando el servicio no responde', () => {
    let fallo = false;
    service.load().subscribe({ error: () => (fallo = true) });

    http
      .expectOne('/api/v1/platform-info')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'sin conexion' });

    expect(fallo).toBe(true);
  });
});
