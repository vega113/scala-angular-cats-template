import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { authCanActivate } from '../../core/guards/auth.guards';

import { TodoEditorPageComponent } from './pages/todo-editor-page/todo-editor-page.component';
import { TodoListPageComponent } from './pages/todo-list-page/todo-list-page.component';

const routes: Routes = [
  {
    path: '',
    component: TodoListPageComponent,
    data: { title: 'My Todos' },
    canActivate: [authCanActivate],
  },
  {
    path: 'new',
    component: TodoEditorPageComponent,
    data: { title: 'Create Todo' },
    canActivate: [authCanActivate],
  },
  {
    path: ':id',
    component: TodoEditorPageComponent,
    data: { title: 'Edit Todo' },
    canActivate: [authCanActivate],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class TodosRoutingModule {}
