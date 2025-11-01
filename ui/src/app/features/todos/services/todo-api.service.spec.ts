import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { TodoApiService } from './todo-api.service';

describe('TodoApiService', () => {
  let service: TodoApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TodoApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists todos', () => {
    let items: string[] = [];
    service.list().subscribe((response) => {
      items = response.items.map((todo) => todo.title);
    });

    const req = httpMock.expectOne('/api/todos');
    req.flush({
      items: [
        {
          id: '1',
          title: 'Example todo',
          description: null,
          dueDate: null,
          completed: false,
          createdAt: '2025-10-31T00:00:00Z',
          updatedAt: '2025-10-31T00:00:00Z',
        },
      ],
      total: 1,
      limit: 20,
      offset: 0,
    });

    expect(items).toEqual(['Example todo']);
  });

  it('creates a todo', () => {
    let createdTitle: string | undefined;
    service.create({ title: 'New', description: null }).subscribe((todo) => {
      createdTitle = todo.title;
    });

    const req = httpMock.expectOne('/api/todos');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: '123',
      title: 'New',
      description: null,
      dueDate: null,
      completed: false,
      createdAt: '2025-10-31T00:00:00Z',
      updatedAt: '2025-10-31T00:00:00Z',
    });

    expect(createdTitle).toBe('New');
  });
});
