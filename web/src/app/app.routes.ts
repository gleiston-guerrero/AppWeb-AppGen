import { Routes } from '@angular/router';

import { administratorGuard, sesionGuard } from './auth/administrator-guard';
import { invitadoGuard } from './auth/invitado-guard';

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
    canActivate: [invitadoGuard],
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
    path: 'sesion-abierta',
    loadComponent: () => import('./auth/session-open-page').then((m) => m.SessionOpenPage),
    title: 'Sesión abierta — SLCP',
  },
  {
    path: 'cuenta/contrasena',
    canActivate: [sesionGuard],
    loadComponent: () => import('./account/password-page').then((m) => m.PasswordPage),
    title: 'Cambiar la contraseña — SLCP',
  },
  {
    path: 'proyecto/:projectId/informe-requisitos',
    canActivate: [sesionGuard],
    loadComponent: () =>
      import('./reports/requirements-report-page').then((m) => m.RequirementsReportPage),
    title: 'Informe de requisitos — SLCP',
  },
  {
    path: 'proyecto/:projectId/especificaciones',
    canActivate: [sesionGuard],
    loadComponent: () =>
      import('./specifications/specifications-page').then((m) => m.SpecificationsPage),
    title: 'Casos de uso e historias — SLCP',
  },
  {
    path: 'proyecto/:projectId/ensayo-ia',
    canActivate: [sesionGuard],
    loadComponent: () => import('./benchmark/benchmark-page').then((m) => m.BenchmarkPage),
    title: 'Comparar proveedores — SLCP',
  },
  {
    path: 'proyecto/:projectId/servicios-ia',
    canActivate: [sesionGuard],
    loadComponent: () => import('./settings/ai-settings-page').then((m) => m.AiSettingsPage),
    title: 'Servicios de IA — SLCP',
  },
  {
    path: 'proyecto/:projectId/generacion',
    canActivate: [sesionGuard],
    loadComponent: () => import('./generation/generation-page').then((m) => m.GenerationPage),
    title: 'Pruebas y diagramas — SLCP',
  },
  {
    path: 'proyecto/:projectId/plan',
    canActivate: [sesionGuard],
    loadComponent: () => import('./planning/planning-page').then((m) => m.PlanningPage),
    title: 'Planificación — SLCP',
  },
  {
    path: 'proyecto/:projectId/entregables',
    canActivate: [sesionGuard],
    loadComponent: () =>
      import('./deliverables/deliverables-page').then((m) => m.DeliverablesPage),
    title: 'Entregables — SLCP',
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
