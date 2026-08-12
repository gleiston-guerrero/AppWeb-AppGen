import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session-service';

/**
 * Impide entrar a la pantalla de acceso con una sesión ya iniciada.
 *
 * Sin esto, quien llega a /entrar teniendo sesión puede autenticarse como otra
 * persona sin haber cerrado la suya, y las dos sesiones se confunden: lo que
 * haga después queda a nombre de quien no lo hizo.
 */
export const invitadoGuard: CanActivateFn = () => {
  const sesion = inject(SessionService);
  const router = inject(Router);

  if (!sesion.autenticado()) {
    return true;
  }

  return router.createUrlTree(['/sesion-abierta']);
};
