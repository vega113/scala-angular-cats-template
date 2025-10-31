import { NgModule } from '@angular/core';

import { SharedModule } from '../../shared/shared.module';
import { TodosRoutingModule } from './todos-routing.module';
import { TodoEditorPageComponent } from './pages/todo-editor-page/todo-editor-page.component';
import { TodoListPageComponent } from './pages/todo-list-page/todo-list-page.component';

@NgModule({
  declarations: [TodoListPageComponent, TodoEditorPageComponent],
  imports: [SharedModule, TodosRoutingModule],
})
export class TodosModule {}
