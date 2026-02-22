export type Role = 'APPRENANT' | 'FORMATEUR';

export interface RegisterRequest {
  prenom: string;
  nom: string;
  email: string;
  telephone?: string;
  password: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
  role: Role;
}

export interface OtpVerifyRequest {
  email: string;
  code: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  email: string;
  code: string;
  newPassword: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  prenom: string;
  nom: string;
  role: Role;
  message: string;
}

export interface MessageResponse {
  message: string;
  success: boolean;
}

export interface UserSession {
  token: string;
  email: string;
  prenom: string;
  nom: string;
  role: Role;
}