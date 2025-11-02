import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { TodoApiService } from '../../services/todo-api.service';
import { TodoListPageComponent } from './todo-list-page.component';

const sampleTodo = {
  id: 'todo-1',
  title: 'Write docs',
  description: null,
  dueDate: null,
  completed: false,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('TodoListPageComponent', () => {
  let fixture: ComponentFixture<TodoListPageComponent>;
  let component: TodoListPageComponent;
  let api: jasmine.SpyObj<TodoApiService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<TodoApiService>('TodoApiService', ['list', 'toggle', 'delete']);
    api.list.and.returnValue(
      of({ items: Array.from({ length: 10 }, () => sampleTodo), total: 10, limit: 10, offset: 0 }),
    );

    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'getCurrentNavigation']);
    router.navigate.and.returnValue(Promise.resolve(true));
    router.getCurrentNavigation.and.returnValue(null);

    await TestBed.configureTestingModule({
      imports: [CommonModule],
      declarations: [TodoListPageComponent],
      providers: [
        { provide: TodoApiService, useValue: api },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TodoListPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads todos with default pagination parameters', () => {
    expect(api.list).toHaveBeenCalledWith({ limit: 10, offset: 0 });
  });

  it('applies completed filter', () => {
    api.list.calls.reset();
    api.list.and.returnValue(of({ items: [], total: 0, limit: 10, offset: 0 }));

    TestBed.runInInjectionContext(() => component.setFilter('done'));

    expect(api.list).toHaveBeenCalledWith({ limit: 10, offset: 0, completed: true });
  });

  it('requests next page when available', () => {
    api.list.calls.reset();
    api.list.and.returnValues(
      of({
        items: Array.from({ length: 10 }, (_, idx) => ({ ...sampleTodo, id: `todo-${idx}` })),
        total: 20,
        limit: 10,
        offset: 0,
      }),
      of({ items: [], total: 20, limit: 10, offset: 10 }),
      of({ items: [], total: 20, limit: 10, offset: 0 }),
    );

    TestBed.runInInjectionContext(() => component.refresh());
    TestBed.runInInjectionContext(() => component.nextPage());

    expect(api.list.calls.argsFor(1)).toEqual([{ limit: 10, offset: 10 }]);
  });
});
