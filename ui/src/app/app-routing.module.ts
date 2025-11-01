import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { authCanMatch } from './core/guards/auth.guards';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'auth/login',
  },
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.module').then((m) => m.AuthModule),
  },
  {
    path: 'todos',
    canMatch: [authCanMatch],
    loadChildren: () => import('./features/todos/todos.module').then((m) => m.TodosModule),
  },
  {
    path: '**',
    redirectTo: 'todos',
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { bindToComponentInputs: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
