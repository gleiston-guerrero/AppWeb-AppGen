import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { SessionService } from './session-service';

/**
 * Impide entrar a las pantallas de administracion sin el rol.
 *
 * Es una comodidad para quien navega, no un control de acceso: el control real
 * lo impone el servicio, conforme a SEC-05. Si esta guarda fallara, una peticion
 * construida a mano tropezaria igualmente contra el servidor.
 */
export const administratorGuard: CanActivateFn = () => {
  const sesion = inject(SessionService);
  const router = inject(Router);

  if (sesion.resuelta()) {
    return sesion.esAdministrador() ? true : router.createUrlTree(['/entrar']);
  }

  return sesion.recuperar().pipe(
    map((s) => (s.platformRole === 'ADMINISTRATOR' ? true : router.createUrlTree(['/entrar']))),
    catchError(() => {
      sesion.descartar();
      return of(router.createUrlTree(['/entrar']));
    }),
  );
};

/**
 * Exige sesión iniciada, sin exigir rol concreto.
 *
 * Igual que la anterior, es comodidad de navegación: el control real lo impone
 * el servicio en cada petición.
 */
export const sesionGuard: CanActivateFn = () => {
  const sesion = inject(SessionService);
  const router = inject(Router);

  if (sesion.resuelta()) {
    return sesion.autenticado() ? true : router.createUrlTree(['/entrar']);
  }

  return sesion.recuperar().pipe(
    map(() => true),
    catchError(() => {
      sesion.descartar();
      return of(router.createUrlTree(['/entrar']));
    }),
  );
};
