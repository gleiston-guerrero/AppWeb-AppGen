import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { RegistrationPage } from './registration-page';

describe('RegistrationPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegistrationPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('FUN-05: exige contrasena y explica la politica', async () => {
    const fixture = TestBed.createComponent(RegistrationPage);
    await fixture.whenStable();
    fixture.detectChanges();

    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('#password')).not.toBeNull();
    expect(raiz.textContent).toContain('15 caracteres');
  });

  it('no envia nada mientras el formulario este incompleto', () => {
    const fixture = TestBed.createComponent(RegistrationPage);
    (fixture.componentInstance as unknown as { enviar: () => void }).enviar();

    http.expectNone('/api/v1/registrations');
  });

  it('al negarse a enviar, dice que falta', () => {
    const fixture = TestBed.createComponent(RegistrationPage);
    const c = fixture.componentInstance as unknown as {
      enviar: () => void;
      error: () => string | null;
    };

    c.enviar();

    expect(c.error()).toContain('Falta algo');
  });

  it('los errores por campo del servicio se muestran bajo su campo', async () => {
    const fixture = TestBed.createComponent(RegistrationPage);
    const c = fixture.componentInstance as unknown as {
      formulario: { setValue: (v: unknown) => void };
      enviar: () => void;
      erroresServidor: () => Record<string, string>;
    };

    c.formulario.setValue({
      fullName: 'Gleiston Guerrero',
      username: 'gguerrero',
      email: 'g@uteq.edu.ec',
      password: 'una frase larga de acceso',
    });
    c.enviar();

    http.expectOne('/api/v1/registrations').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'Revise los datos indicados',
        fields: { username: 'Ese nombre ya esta en uso' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(c.erroresServidor()['username']).toContain('ya esta en uso');
  });

  it('FUN-15: no ofrece eleccion de rol', async () => {
    const fixture = TestBed.createComponent(RegistrationPage);
    await fixture.whenStable();
    fixture.detectChanges();

    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('select')).toBeNull();
    expect(raiz.querySelector('[formcontrolname="role"]')).toBeNull();
  });
});
