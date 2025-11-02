import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { TodoApiService } from '../../services/todo-api.service';
import { TodoEditorPageComponent } from './todo-editor-page.component';

describe('TodoEditorPageComponent', () => {
  let fixture: ComponentFixture<TodoEditorPageComponent>;
  let component: TodoEditorPageComponent;
  let api: jasmine.SpyObj<TodoApiService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<TodoApiService>('TodoApiService', [
      'create',
      'update',
      'get',
      'list',
      'delete',
      'toggle',
    ]);

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.returnValue(Promise.resolve(true));

    await TestBed.configureTestingModule({
      imports: [CommonModule, ReactiveFormsModule],
      declarations: [TodoEditorPageComponent],
      providers: [
        { provide: TodoApiService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({})) },
        },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TodoEditorPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('enables submit button when title is provided', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    );
    expect(button.disabled).toBeTrue();

    component.form.controls.title.setValue('Write tests');
    fixture.detectChanges();

    expect(button.disabled).toBeFalse();
  });

  it('creates a new todo and navigates back to the list', () => {
    const navigateState = { replaceUrl: true, state: { createdTodoId: 'todo-1' } };
    component.form.controls.title.setValue('Write tests');
    fixture.detectChanges();

    api.create.and.returnValue(
      of({
        id: 'todo-1',
        title: 'Write tests',
        description: null,
        dueDate: null,
        completed: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }),
    );

    TestBed.runInInjectionContext(() => component.submit());

    expect(api.create).toHaveBeenCalledWith({
      title: 'Write tests',
      description: null,
      dueDate: null,
    });
    expect(router.navigate).toHaveBeenCalledWith(
      ['/todos'],
      jasmine.objectContaining(navigateState),
    );
  });
});
