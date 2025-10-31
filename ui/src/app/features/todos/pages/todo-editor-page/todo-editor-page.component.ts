import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

@Component({
  selector: 'app-todo-editor-page',
  templateUrl: './todo-editor-page.component.html',
  styleUrls: ['./todo-editor-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodoEditorPageComponent {
  readonly todoId$ = this.route.paramMap.pipe(map((params) => params.get('id')));

  constructor(private readonly route: ActivatedRoute) {}
}
