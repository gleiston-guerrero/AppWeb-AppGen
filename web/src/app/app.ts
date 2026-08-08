import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Armazon de la aplicacion: cabecera de navegacion y area de contenido.
 *
 * La cabecera es visible para el visitante sin sesion iniciada, conforme a
 * FUN-01. Las opciones que exigen sesion aparecen, pero declaradas como no
 * disponibles mientras el incremento correspondiente no exista: ocultarlas
 * daria una idea equivocada del alcance del producto.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
