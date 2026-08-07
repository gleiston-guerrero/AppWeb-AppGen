import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./platform-info/platform-info-page').then((m) => m.PlatformInfoPage),
    title: 'SLCP',
  },
  { path: '**', redirectTo: '' },
];
