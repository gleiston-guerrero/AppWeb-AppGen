import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { SessionService } from './session-service';

/** Rutas que no deben intentar renovar: son las que dan o quitan la sesión. */
const SIN_RENOVACION = [
  '/api/v1/auth/sessions',
  '/api/v1/registrations',
  '/api/v1/password-resets',
  '/api/v1/invitations',
];

/**
 * Renueva la sesión cuando caduca, y reintenta la petición.
 *
 * El token de acceso dura quince minutos. Sin esta renovación, quien lleva un
 * rato trabajando ve fallar todos los botones a la vez, y el síntoma —«no
 * funciona nada»— no se parece en nada a la causa.
 *
 * Se renueva al recibir la negativa y no por temporizador: un temporizador
 * renovaría sesiones de pestañas olvidadas, manteniéndolas vivas
 * indefinidamente sin que nadie las use.
 */
export const sessionRefreshInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SessionService);

  return siguiente(peticion).pipe(
    catchError((fallo: unknown) => {
      const esCaducidad =
        fallo instanceof HttpErrorResponse &&
        fallo.status === 401 &&
        !SIN_RENOVACION.some((ruta) => peticion.url.startsWith(ruta));

      if (!esCaducidad) {
        return throwError(() => fallo);
      }

      return sesion.renovar().pipe(
        // Renovada la sesión, se repite la petición original: quien pulsó el
        // botón no tiene por qué volver a pulsarlo.
        switchMap(() => siguiente(peticion)),
        catchError((segundoFallo: unknown) => {
          // La renovación tampoco valió: la sesión está de verdad terminada.
          sesion.descartar();
          return throwError(() => segundoFallo);
        }),
      ) as Observable<never>;
    }),
  );
};
