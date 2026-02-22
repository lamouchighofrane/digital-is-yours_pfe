import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import {
  RegisterRequest, LoginRequest, OtpVerifyRequest,
  ForgotPasswordRequest, ResetPasswordRequest,
  AuthResponse, MessageResponse, UserSession
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly API = 'http://localhost:8080/api/auth';
  private readonly TOKEN_KEY = 'diy_token';
  private readonly USER_KEY  = 'diy_user';

  constructor(private http: HttpClient) {}

  register(data: RegisterRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API}/register`, data);
  }

  verifyOtp(data: OtpVerifyRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/verify-otp`, data).pipe(
      tap(res => this.saveUser(res))
    );
  }

  resendOtp(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API}/resend-otp?email=${email}`, {});
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/login`, data).pipe(
      tap(res => this.saveUser(res))
    );
  }

  forgotPassword(data: ForgotPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API}/forgot-password`, data);
  }

  resetPassword(data: ResetPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API}/reset-password`, data);
  }

  saveUser(res: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, res.token);
    const user: UserSession = {
      token: res.token, email: res.email,
      prenom: res.prenom, nom: res.nom, role: res.role
    };
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getCurrentUser(): UserSession | null {
    const u = localStorage.getItem(this.USER_KEY);
    return u ? JSON.parse(u) : null;
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }
}