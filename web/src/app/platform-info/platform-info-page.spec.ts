import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PlatformInfo } from './platform-info';
import { PlatformInfoPage } from './platform-info-page';

/**
 * Oraculo de la pantalla publica.
 *
 * Comprueba lo que FUN-01 y FUN-02 exigen mostrar, y comprueba tambien el caso
 * de servicio caido, que es el que la persona encontrara con mas frecuencia
 * durante el desarrollo.
 */
describe('PlatformInfoPage', () => {
  const ejemplo: PlatformInfo = {
    name: 'SLCP',
    version: '0.1.0-TEST',
    purpose: 'Genera aplicaciones web a partir de requisitos',
    input: 'La especificacion de requisitos del software',
    produces: ['Esquema relacional', 'Codigo fuente de la logica de negocio'],
    builtWith: ['Java 21', 'Spring Boot 4.1', 'Angular'],
    authorship: 'Gleiston Guerrero-Ulloa, Universidad Tecnica Estatal de Quevedo',
    license: 'MIT',
    repository: 'https://github.com/gleiston-guerrero/AppWeb-AppGen',
  };

  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformInfoPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  it('FUN-01: muestra el proposito, el insumo y lo que produce', async () => {
    const fixture = TestBed.createComponent(PlatformInfoPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/platform-info').flush(ejemplo);
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain(ejemplo.purpose);
    expect(texto).toContain(ejemplo.input);
    expect(texto).toContain('Esquema relacional');
    expect(texto).toContain('Codigo fuente de la logica de negocio');
  });

  it('FUN-02: muestra la autoria y la licencia', async () => {
    const fixture = TestBed.createComponent(PlatformInfoPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/platform-info').flush(ejemplo);
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Quevedo');
    expect(texto).toContain('MIT');
  });

  it('explica que hacer cuando el servicio no responde', async () => {
    const fixture = TestBed.createComponent(PlatformInfoPage);
    fixture.detectChanges();

    http
      .expectOne('/api/v1/platform-info')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'sin conexion' });
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('8081');
  });
});
