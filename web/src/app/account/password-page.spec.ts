import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PasswordPage } from './password-page';

describe('PasswordPage', () => {
  let http: HttpTestingController;

  const URL = '/api/v1/auth/sessions/current/password';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PasswordPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('exige la contrasena actual aunque haya sesion', async () => {
    const fixture = TestBed.createComponent(PasswordPage);
    await fixture.whenStable();
    fixture.detectChanges();

    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('#actual')).not.toBeNull();
    expect(raiz.textContent).toContain('sesión desatendida');
  });

  it('no envia nada con el formulario incompleto, y dice que falta', () => {
    const fixture = TestBed.createComponent(PasswordPage);
    const c = fixture.componentInstance as unknown as {
      cambiar: () => void;
      error: () => string | null;
    };

    c.cambiar();

    http.expectNone(URL);
    expect(c.error()).toContain('Falta algo');
  });

  it('rechaza que la contrasena nueva sea la misma que la actual', () => {
    const fixture = TestBed.createComponent(PasswordPage);
    const c = fixture.componentInstance as unknown as {
      formulario: { setValue: (v: unknown) => void };
      cambiar: () => void;
      error: () => string | null;
    };

    c.formulario.setValue({
      currentPassword: 'una frase larga de acceso',
      newPassword: 'una frase larga de acceso',
    });
    c.cambiar();

    http.expectNone(URL);
    expect(c.error()).toContain('misma');
  });

  it('envia ambas contrasenas y avisa del cierre de sesiones', async () => {
    const fixture = TestBed.createComponent(PasswordPage);
    const c = fixture.componentInstance as unknown as {
      formulario: { setValue: (v: unknown) => void };
      cambiar: () => void;
      hecho: () => boolean;
    };

    c.formulario.setValue({
      currentPassword: 'la contrasena actual larga',
      newPassword: 'una frase nueva y distinta',
    });
    c.cambiar();

    const peticion = http.expectOne(URL);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body.currentPassword).toBe('la contrasena actual larga');
    peticion.flush(null);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(c.hecho()).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('sesiones abiertas');
  });

  it('el mensaje del servicio se muestra, no uno generico', async () => {
    const fixture = TestBed.createComponent(PasswordPage);
    const c = fixture.componentInstance as unknown as {
      formulario: { setValue: (v: unknown) => void };
      cambiar: () => void;
      error: () => string | null;
    };

    c.formulario.setValue({
      currentPassword: 'esta no es la correcta',
      newPassword: 'una frase nueva y distinta',
    });
    c.cambiar();

    http.expectOne(URL).flush(
      { code: 'PASSWORD_RECOVERY', message: 'La contrasena actual no es correcta', fields: {} },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await fixture.whenStable();

    expect(c.error()).toContain('actual no es correcta');
  });
});
