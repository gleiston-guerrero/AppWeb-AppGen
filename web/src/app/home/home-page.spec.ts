import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PlatformInfo } from '../platform-info/platform-info';
import { HomePage } from './home-page';

describe('HomePage', () => {
  const ejemplo: PlatformInfo = {
    name: 'SLCP',
    version: '0.1.0-TEST',
    purpose: 'Genera aplicaciones web a partir de requisitos',
    input: 'La especificacion de requisitos del software',
    produces: ['Esquema relacional', 'Codigo fuente de la logica de negocio'],
    builtWith: ['Java 21', 'Angular'],
    authorship: 'Gleiston Guerrero-Ulloa, Universidad Tecnica Estatal de Quevedo',
    license: 'MIT',
    repository: 'https://github.com/gleiston-guerrero/AppWeb-AppGen',
  };

  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  it('FUN-01: muestra la materia prima y lo que produce', async () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform-info').flush(ejemplo);
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain(ejemplo.input);
    expect(texto).toContain('Esquema relacional');
  });

  it('FUN-02: muestra la autoria y la licencia', async () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform-info').flush(ejemplo);
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Quevedo');
    expect(texto).toContain('MIT');
  });

  it('el ejemplo del encabezado se muestra sin depender del servicio', async () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform-info').error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('REQ-MAT-0017-v1');
    expect(texto).toContain('8081');
  });
});
