import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import {
  Todo,
  TodoCreateRequest,
  TodoListResponse,
  TodoUpdateRequest,
} from '../models/todo.model';

interface TodoDto {
  id: string;
  title: string;
  description?: string | null;
  dueDate?: string | null;
  completed: boolean;
  createdAt: string;
  updatedAt: string;
}

interface TodoListDto {
  items: TodoDto[];
  total: number;
  limit: number;
  offset: number;
}

export interface TodoListParams {
  completed?: boolean;
  limit?: number;
  offset?: number;
}

@Injectable({ providedIn: 'root' })
export class TodoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/todos';

  list(params: TodoListParams = {}): Observable<TodoListResponse> {
    let httpParams = new HttpParams();
    if (params.completed !== undefined) {
      httpParams = httpParams.set('completed', params.completed ? 'true' : 'false');
    }
    if (params.limit !== undefined) {
      httpParams = httpParams.set('limit', params.limit.toString());
    }
    if (params.offset !== undefined) {
      httpParams = httpParams.set('offset', params.offset.toString());
    }

    return this.http
      .get<TodoListDto | TodoDto[]>(this.baseUrl, { params: httpParams })
      .pipe(map(mapTodoListResponse));
  }

  get(id: string): Observable<Todo> {
    return this.http
      .get<TodoDto>(`${this.baseUrl}/${encodeURIComponent(id)}`)
      .pipe(map(mapTodo));
  }

  create(payload: TodoCreateRequest): Observable<Todo> {
    return this.http.post<TodoDto>(this.baseUrl, payload).pipe(map(mapTodo));
  }

  update(id: string, payload: TodoUpdateRequest): Observable<Todo> {
    return this.http
      .put<TodoDto>(`${this.baseUrl}/${encodeURIComponent(id)}`, payload)
      .pipe(map(mapTodo));
  }

  toggle(id: string): Observable<Todo> {
    return this.http
      .patch<TodoDto>(`${this.baseUrl}/${encodeURIComponent(id)}/toggle`, {})
      .pipe(map(mapTodo));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
}

function mapTodo(dto: TodoDto): Todo {
  return {
    id: dto.id,
    title: dto.title,
    description: dto.description ?? null,
    dueDate: dto.dueDate ?? null,
    completed: dto.completed,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
  };
}

function mapTodoListResponse(dto: TodoListDto | TodoDto[]): TodoListResponse {
  if (Array.isArray(dto)) {
    return {
      items: dto.map(mapTodo),
      total: dto.length,
      limit: dto.length,
      offset: 0,
    };
  }

  return {
    items: dto.items.map(mapTodo),
    total: dto.total,
    limit: dto.limit,
    offset: dto.offset,
  };
}
