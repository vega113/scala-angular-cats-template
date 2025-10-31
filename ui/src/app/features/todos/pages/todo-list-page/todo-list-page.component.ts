import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-todo-list-page',
  templateUrl: './todo-list-page.component.html',
  styleUrls: ['./todo-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodoListPageComponent {
  readonly placeholderItems = [
    { id: '1', title: 'Wire up UI with backend', completed: false },
    { id: '2', title: 'Add JWT interceptor', completed: false },
    { id: '3', title: 'Polish Heroku deploy', completed: true },
  ];
}
