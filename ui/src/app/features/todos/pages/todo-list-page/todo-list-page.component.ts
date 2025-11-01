import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { Router } from '@angular/router';

import { Todo } from '../../models/todo.model';
import { TodoApiService } from '../../services/todo-api.service';

@Component({
  selector: 'app-todo-list-page',
  templateUrl: './todo-list-page.component.html',
  styleUrls: ['./todo-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodoListPageComponent {
  private readonly todoApi = inject(TodoApiService);
  private readonly router = inject(Router);

  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);
  readonly todos = signal<Todo[]>([]);
  readonly refreshing = computed(
    () => this.loading() && !this.todos().length && !this.errorMessage(),
  );
  readonly hasTodos = computed(() => this.todos().length > 0);
  readonly hasError = computed(() => this.errorMessage() !== null);

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.todoApi
      .list()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (response) => {
          this.todos.set(response.items);
        },
        error: (error: unknown) => {
          console.error('Failed to load todos', error);
          this.errorMessage.set('Failed to load todos. Please try again.');
        },
      });
  }

  createTodo(): void {
    this.router.navigate(['/todos/new']);
  }

  editTodo(todo: Todo): void {
    this.router.navigate(['/todos', todo.id]);
  }

  toggleCompletion(todo: Todo): void {
    this.loading.set(true);
    this.todoApi
      .toggle(todo.id)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (updated) => {
          this.todos.set(
            this.todos().map((item) =>
              item.id === updated.id ? updated : item,
            ),
          );
        },
        error: (error: unknown) => {
          console.error('Failed to toggle todo', error);
          this.errorMessage.set('Unable to update todo. Please try again.');
        },
      });
  }

  deleteTodo(todo: Todo): void {
    if (!confirm(`Delete "${todo.title}"?`)) {
      return;
    }
    this.loading.set(true);
    this.todoApi
      .delete(todo.id)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: () => {
          this.todos.set(this.todos().filter((item) => item.id !== todo.id));
        },
        error: (error: unknown) => {
          console.error('Failed to delete todo', error);
          this.errorMessage.set('Unable to delete todo. Please try again.');
        },
      });
  }

  trackById(_: number, todo: Todo): string {
    return todo.id;
  }
}
