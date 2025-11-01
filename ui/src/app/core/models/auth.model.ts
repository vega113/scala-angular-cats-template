export interface AuthUser {
  id: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  user: AuthUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest extends LoginRequest {}

export interface PasswordResetRequestPayload {
  email: string;
}

export interface PasswordResetConfirmPayload {
  token: string;
  password: string;
}
