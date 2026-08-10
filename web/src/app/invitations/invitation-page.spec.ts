import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { InvitationPreview } from './invitation';
import { InvitationPage } from './invitation-page';

describe('InvitationPage', () => {
  let http: HttpTestingController;

  const invitacion: InvitationPreview = {
    valid: true,
    reason: '',
    projectName: 'MundiPets',
    projectReadableId: 'PRJ-0001-v1',
    invitedBy: 'Gleiston Guerrero',
    email: 'nuevo@uteq.edu.ec',
    role: 'TEAM_MEMBER',
    roleLabel: 'Miembro del equipo',
    roleScope: 'Trabaja los requisitos, genera y modifica artefactos.',
    requiereRegistro: true,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InvitationPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'token-de-prueba' } } },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  it('muestra el proyecto, quien invita y el rol', async () => {
    const fixture = TestBed.createComponent(InvitationPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/invitations/token-de-prueba').flush(invitacion);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('MundiPets');
    expect(texto).toContain('Gleiston Guerrero');
    expect(texto).toContain('Miembro del equipo');
  });

  it('INV-02: el rol no aparece como campo editable', async () => {
    const fixture = TestBed.createComponent(InvitationPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations/token-de-prueba').flush(invitacion);
    await fixture.whenStable();
    fixture.detectChanges();

    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('select')).toBeNull();
    expect(raiz.querySelector('input[name="role"]')).toBeNull();
    expect(raiz.querySelector('input[formcontrolname="role"]')).toBeNull();
  });

  /**
   * INV-03 prohibe revelar el contenido del proyecto a una direccion todavia no
   * verificada. Lo que se comprueba es que no aparezca ningun artefacto
   * identificable, no que falte una palabra: la descripcion del rol menciona los
   * requisitos como parte de su cometido, y eso es legitimo.
   */
  it('INV-03: no revela ningun artefacto del proyecto antes de aceptar', async () => {
    const fixture = TestBed.createComponent(InvitationPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations/token-de-prueba').flush(invitacion);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    // Ningun identificador de artefacto: requisitos, escenarios, diseno, codigo
    // ni pruebas del proyecto.
    expect(texto).not.toMatch(/\b(REQ|RF|RNF|SCN|DSN|CDU|TST)-\d/);

    // Ningun integrante del equipo mas alla de quien invita.
    expect(texto).not.toContain('Equipo');
    expect(texto).not.toContain('integrantes');
  });

  it('INV-03: solo se muestran los cuatro datos autorizados', async () => {
    const fixture = TestBed.createComponent(InvitationPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations/token-de-prueba').flush(invitacion);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    // Lo que si debe verse: proyecto, quien invita, correo y rol.
    expect(texto).toContain(invitacion.projectName);
    expect(texto).toContain(invitacion.invitedBy);
    expect(texto).toContain(invitacion.email);
    expect(texto).toContain(invitacion.roleLabel);

    // Y nada del estado interno del proyecto.
    expect(texto).not.toContain('ACTIVE');
    expect(texto).not.toContain('createdAt');
  });

  it('un enlace no valido explica por que', async () => {
    const fixture = TestBed.createComponent(InvitationPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations/token-de-prueba').flush({
      ...invitacion,
      valid: false,
      reason: 'Ese enlace ya se uso',
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('ya se uso');
  });

  it('quien ya tiene cuenta no ve formulario de registro', async () => {
    const fixture = TestBed.createComponent(InvitationPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations/token-de-prueba').flush({
      ...invitacion,
      requiereRegistro: false,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('input[formcontrolname="password"]')).toBeNull();
  });
});
