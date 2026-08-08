import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home-page').then((m) => m.HomePage),
    title: 'SLCP — De una especificación a una aplicación',
  },
  {
    path: 'solicitar-registro',
    loadComponent: () =>
      import('./registration/registration-page').then((m) => m.RegistrationPage),
    title: 'Solicitar registro — SLCP',
  },
  { path: '**', redirectTo: '' },
];
