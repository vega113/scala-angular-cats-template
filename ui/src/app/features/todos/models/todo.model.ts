export interface Todo {
  id: string;
  title: string;
  description?: string | null;
  dueDate?: string | null;
  completed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TodoListResponse {
  items: Todo[];
  total: number;
  limit: number;
  offset: number;
}

export interface TodoCreateRequest {
  title: string;
  description?: string | null;
  dueDate?: string | null;
}

export interface TodoUpdateRequest {
  title?: string;
  description?: string | null;
  dueDate?: string | null;
  completed?: boolean;
}
