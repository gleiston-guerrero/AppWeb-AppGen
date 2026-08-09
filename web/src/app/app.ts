import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { SessionService } from './auth/session-service';

/**
 * Armazon de la aplicacion: cabecera de navegacion y area de contenido.
 *
 * La cabecera cambia segun haya sesion o no. Lo que muestra es una comodidad,
 * nunca un control: SEC-05 exige que toda restriccion este impuesta en el
 * servicio, de modo que ocultar una opcion no impide nada por si solo.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly servicio = inject(SessionService);

  protected readonly sesion = this.servicio.sesion;
  protected readonly esAdministrador = this.servicio.esAdministrador;
}
