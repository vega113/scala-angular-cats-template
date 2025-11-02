import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

import { Todo } from '../../models/todo.model';
import { TodoApiService } from '../../services/todo-api.service';

@Component({
  selector: 'app-todo-editor-page',
  templateUrl: './todo-editor-page.component.html',
  styleUrls: ['./todo-editor-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodoEditorPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly todoApi = inject(TodoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    dueDate: [''],
    completed: [false],
  });

  readonly loading = signal<boolean>(false);
  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  private readonly currentTodo = signal<Todo | null>(null);

  readonly isEditMode = computed(() => this.currentTodo() !== null);
  readonly pageTitle = computed(() =>
    this.isEditMode() ? 'Edit Todo' : 'Create Todo',
  );

  constructor() {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          const id = params.get('id');
          if (!id) {
            this.currentTodo.set(null);
            this.form.reset({
              title: '',
              description: '',
              dueDate: '',
              completed: false,
            });
            return of(null);
          }
          this.loading.set(true);
          this.errorMessage.set(null);
          return this.todoApi.get(id).pipe(finalize(() => this.loading.set(false)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (todo) => {
          if (!todo) {
            return;
          }
          this.currentTodo.set(todo);
          this.form.reset({
            title: todo.title ?? '',
            description: todo.description ?? '',
            dueDate: todo.dueDate ? toDateInputValue(todo.dueDate) : '',
            completed: todo.completed,
          });
        },
        error: (error: unknown) => {
          console.error('Failed to load todo', error);
          this.errorMessage.set('Unable to load todo. Please try again.');
        },
      });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const title = this.form.value.title?.trim() ?? '';
    const description = (this.form.value.description ?? '').trim() || null;
    const dueDate = this.form.value.dueDate
      ? new Date(this.form.value.dueDate).toISOString()
      : null;

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const todo = this.currentTodo();
    const request$ = todo
      ? this.todoApi.update(todo.id, {
          title,
          description,
          dueDate,
          completed: this.form.value.completed ?? false,
        })
      : this.todoApi.create({
          title,
          description,
          dueDate,
        });

    request$
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed())
      .subscribe({
        next: (result) => {
          if (!todo) {
            this.currentTodo.set(result);
            this.router.navigate(['/todos'], {
              replaceUrl: true,
              state: { createdTodoId: result.id },
            });
          } else {
            this.successMessage.set('Todo updated successfully.');
            this.currentTodo.set(result);
            this.form.patchValue({
              title: result.title ?? '',
              description: result.description ?? '',
              dueDate: result.dueDate ? toDateInputValue(result.dueDate) : '',
              completed: result.completed,
            });
          }
        },
        error: (error: unknown) => {
          console.error('Failed to save todo', error);
          this.errorMessage.set('Unable to save todo. Please try again.');
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/todos']);
  }

  openDatePicker(input: HTMLInputElement): void {
    const picker = input as HTMLInputElement & { showPicker?: () => void };
    if (typeof picker.showPicker === 'function') {
      picker.showPicker();
    } else {
      input.focus();
      input.click();
    }
  }

  isSubmitDisabled(): boolean {
    return this.submitting() || this.form.invalid;
  }
}

function toDateInputValue(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toISOString().split('T')[0] ?? '';
}
