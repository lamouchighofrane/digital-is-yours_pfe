import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

type FpStep = 1 | 2 | 3 | 4;

@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.css']
})
export class ForgotPasswordComponent implements OnDestroy {

  currentStep: FpStep = 1;
  isLoading = false;
  errorMessage = '';
  successMessage = '';
  otpValues: string[] = ['', '', '', '', '', ''];
  countdown = 300;
  showNewPassword = false;
  showConfirmPassword = false;
  private timer: any;

  emailForm: FormGroup;
  passwordForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private authService: AuthService
  ) {
    this.emailForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });
    this.passwordForm = this.fb.group({
      newPassword:     ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    });
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  // ── Étape 1 : Envoyer le code par email ─────────────────────────
  sendOtp() {
    if (this.emailForm.invalid) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.isLoading = true;
    this.errorMessage = '';

    this.authService.forgotPassword({ email: this.emailForm.value.email }).subscribe({
      next: () => {
        this.isLoading = false;
        this.currentStep = 2;
        this.startCountdown();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Aucun compte trouvé avec cet email';
      }
    });
  }

  // ── Étape 2 : Vérifier le code OTP ──────────────────────────────
  // CORRIGÉ : on passe directement à l'étape 3 si le code est complet
  // La vraie vérification se fait lors du reset final
  verifyCode() {
    const code = this.otpValues.join('');
    if (code.length !== 6) {
      this.errorMessage = 'Veuillez entrer les 6 chiffres du code';
      return;
    }
    this.errorMessage = '';
    // On arrête le timer et on passe au formulaire nouveau mot de passe
    clearInterval(this.timer);
    this.currentStep = 3;
  }

  // ── Étape 3 : Réinitialiser le mot de passe ─────────────────────
  resetPassword() {
    const p = this.passwordForm.value;

    if (p.newPassword !== p.confirmPassword) {
      this.errorMessage = 'Les mots de passe ne correspondent pas';
      return;
    }
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.resetPassword({
      email:       this.emailForm.value.email,
      code:        this.otpValues.join(''),
      newPassword: p.newPassword
    }).subscribe({
      next: () => {
        this.isLoading = false;
        // Passer à l'étape 4 : succès
        this.currentStep = 4;
        // Redirection automatique vers login après 2.5 secondes
        setTimeout(() => this.router.navigate(['/login']), 2500);
      },
      error: (err) => {
        this.isLoading = false;
        // Si le code est expiré → retourner à l'étape 2 pour re-saisir
        if (err.error?.message?.includes('expiré') || err.error?.message?.includes('invalide')) {
          this.errorMessage = 'Code expiré ou invalide. Demandez un nouveau code.';
          this.currentStep = 2;
          this.otpValues = ['', '', '', '', '', ''];
          this.startCountdown();
        } else {
          this.errorMessage = err.error?.message || 'Une erreur est survenue';
        }
      }
    });
  }

  // ── Timer ────────────────────────────────────────────────────────
  private startCountdown() {
    this.countdown = 300;
    if (this.timer) clearInterval(this.timer);
    this.timer = setInterval(() => {
      if (this.countdown > 0) this.countdown--;
      else clearInterval(this.timer);
    }, 1000);
  }

  get countdownDisplay(): string {
    const m = Math.floor(this.countdown / 60).toString().padStart(2, '0');
    const s = (this.countdown % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  get canResend(): boolean { return this.countdown === 0; }

  // ── Renvoyer le code ─────────────────────────────────────────────
  resendCode() {
    if (!this.canResend) return;
    this.otpValues = ['', '', '', '', '', ''];
    this.errorMessage = '';
    this.isLoading = true;

    this.authService.forgotPassword({ email: this.emailForm.value.email }).subscribe({
      next: () => {
        this.isLoading = false;
        this.startCountdown();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Erreur lors du renvoi';
      }
    });
  }

  // ── OTP input handlers ───────────────────────────────────────────
  onOtpInput(event: Event, index: number) {
    const input = event.target as HTMLInputElement;
    const value = input.value.replace(/\D/g, '').slice(-1);
    this.otpValues[index] = value;
    input.value = value;
    if (value && index < 5) {
      const next = document.getElementById(`fp-otp-${index + 1}`) as HTMLInputElement;
      if (next) next.focus();
    }
    // Auto-passer à l'étape suivante si les 6 chiffres sont saisis
    if (this.otpValues.every(v => v !== '')) {
      setTimeout(() => this.verifyCode(), 300);
    }
  }

  onOtpKeydown(event: KeyboardEvent, index: number) {
    if (event.key === 'Backspace' && !this.otpValues[index] && index > 0) {
      const prev = document.getElementById(`fp-otp-${index - 1}`) as HTMLInputElement;
      if (prev) { prev.focus(); this.otpValues[index - 1] = ''; }
    }
  }

  onOtpPaste(event: ClipboardEvent) {
    event.preventDefault();
    const text = event.clipboardData?.getData('text').replace(/\D/g, '').slice(0, 6) || '';
    text.split('').forEach((char, i) => { if (i < 6) this.otpValues[i] = char; });
    if (text.length === 6) {
      setTimeout(() => this.verifyCode(), 300);
    }
  }

  isInvalid(field: string): boolean {
    const ctrl = this.passwordForm.get(field);
    return !!(ctrl?.invalid && ctrl?.touched);
  }

  goToLogin() { this.router.navigate(['/login']); }
}