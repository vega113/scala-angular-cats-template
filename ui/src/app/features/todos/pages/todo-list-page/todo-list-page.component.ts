import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { Router } from '@angular/router';

import { Todo } from '../../models/todo.model';
import { TodoApiService, TodoListParams } from '../../services/todo-api.service';

type TodoFilter = 'all' | 'open' | 'done';

@Component({
  selector: 'app-todo-list-page',
  templateUrl: './todo-list-page.component.html',
  styleUrls: ['./todo-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodoListPageComponent {
  private readonly todoApi = inject(TodoApiService);
  private readonly router = inject(Router);

  private readonly pageSize = 10;
  private readonly offset = signal<number>(0);
  private readonly filter = signal<TodoFilter>('all');

  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);
  readonly todos = signal<Todo[]>([]);
  private readonly flashSignal = signal<string | null>(null);
  private readonly hasNextPageSignal = signal<boolean>(false);
  readonly refreshing = computed(
    () => this.loading() && !this.todos().length && !this.errorMessage(),
  );
  readonly hasTodos = computed(() => this.todos().length > 0);
  readonly hasError = computed(() => this.errorMessage() !== null);
  readonly currentFilter = computed(() => this.filter());
  readonly hasNextPage = computed(() => this.hasNextPageSignal());
  readonly hasPreviousPage = computed(() => this.offset() > 0);
  readonly flashMessage = computed(() => this.flashSignal());

  constructor() {
    const navigation = this.router.getCurrentNavigation();
    const createdTodoId = navigation?.extras?.state?.['createdTodoId'];
    if (createdTodoId) {
      this.flashSignal.set('Todo created successfully.');
      if (typeof window !== 'undefined' && window.history && window.history.replaceState) {
        const currentState = (window.history.state ?? {}) as Record<string, unknown>;
        const nextState: Record<string, unknown> = { ...currentState };
        delete nextState['createdTodoId'];
        window.history.replaceState(nextState, document.title);
      }
    }
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const params: TodoListParams = {
      limit: this.pageSize,
      offset: this.offset(),
    };

    const filter = this.filter();
    if (filter !== 'all') {
      params.completed = filter === 'done';
    }

    this.todoApi
      .list(params)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (response) => {
          if (response.items.length === 0 && this.offset() > 0) {
            this.offset.update((value) => Math.max(0, value - this.pageSize));
            this.refresh();
            return;
          }

          this.todos.set(response.items);
          this.hasNextPageSignal.set(response.items.length === this.pageSize);
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

  setFilter(filter: TodoFilter): void {
    if (this.filter() === filter && this.offset() === 0) {
      return;
    }
    this.filter.set(filter);
    this.offset.set(0);
    this.refresh();
  }

  nextPage(): void {
    if (!this.hasNextPage()) {
      return;
    }
    this.offset.update((value) => value + this.pageSize);
    this.refresh();
  }

  previousPage(): void {
    if (!this.hasPreviousPage()) {
      return;
    }
    this.offset.update((value) => Math.max(0, value - this.pageSize));
    this.refresh();
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
        next: () => this.refresh(),
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
        next: () => this.refresh(),
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
