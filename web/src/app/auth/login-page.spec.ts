import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('FUN-03: admite indistintamente usuario o correo', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Usuario o correo');
  });

  it('muestra el mensaje que devuelve el servicio, no uno generico', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    const componente = fixture.componentInstance as unknown as {
      formulario: { setValue: (v: unknown) => void };
      enviar: () => void;
    };

    componente.formulario.setValue({ identifier: 'gguerrero', password: 'lo que sea' });
    componente.enviar();

    http.expectOne('/api/v1/auth/sessions').flush(
      {
        code: 'PENDING_APPROVAL',
        message: 'Su solicitud de registro aun no ha sido aprobada',
        fields: {},
      },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('aun no ha sido aprobada');
  });

  it('el bloqueo por intentos se distingue de un error de credenciales', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    const componente = fixture.componentInstance as unknown as {
      formulario: { setValue: (v: unknown) => void };
      enviar: () => void;
      bloqueado: () => boolean;
    };

    componente.formulario.setValue({ identifier: 'gguerrero', password: 'x' });
    componente.enviar();

    http.expectOne('/api/v1/auth/sessions').flush(
      { code: 'TOO_MANY_ATTEMPTS', message: 'Demasiados intentos fallidos', fields: {} },
      { status: 429, statusText: 'Too Many Requests' },
    );
    await fixture.whenStable();

    expect(componente.bloqueado()).toBe(true);
  });

  it('no envia nada con el formulario vacio', () => {
    const fixture = TestBed.createComponent(LoginPage);
    (fixture.componentInstance as unknown as { enviar: () => void }).enviar();

    http.expectNone('/api/v1/auth/sessions');
  });
});
