import { Routes } from '@angular/router';

import { administratorGuard, sesionGuard } from './auth/administrator-guard';

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
  {
    path: 'invitacion/:token',
    loadComponent: () =>
      import('./invitations/invitation-page').then((m) => m.InvitationPage),
    title: 'Invitación a un proyecto — SLCP',
  },
  {
    path: 'recuperar',
    loadComponent: () => import('./recovery/recovery-page').then((m) => m.RecoveryPage),
    title: 'Recuperar el acceso — SLCP',
  },
  {
    path: 'recuperar/:token',
    loadComponent: () => import('./recovery/recovery-page').then((m) => m.RecoveryPage),
    title: 'Nueva contraseña — SLCP',
  },
  {
    path: 'entrar',
    loadComponent: () => import('./auth/login-page').then((m) => m.LoginPage),
    title: 'Iniciar sesión — SLCP',
  },
  {
    path: 'trabajo',
    canActivate: [sesionGuard],
    loadComponent: () => import('./projects/workspace-page').then((m) => m.WorkspacePage),
    title: 'Espacio de trabajo — SLCP',
  },
  {
    path: 'proyecto/:projectId/requisitos',
    canActivate: [sesionGuard],
    loadComponent: () =>
      import('./requirements/requirements-page').then((m) => m.RequirementsPage),
    title: 'Requisitos — SLCP',
  },
  {
    path: 'cuenta/contrasena',
    canActivate: [sesionGuard],
    loadComponent: () => import('./account/password-page').then((m) => m.PasswordPage),
    title: 'Cambiar la contraseña — SLCP',
  },
  {
    path: 'administracion',
    canActivate: [administratorGuard],
    loadComponent: () =>
      import('./administration/administration-page').then((m) => m.AdministrationPage),
    title: 'Administración — SLCP',
  },
  { path: '**', redirectTo: '' },
];
