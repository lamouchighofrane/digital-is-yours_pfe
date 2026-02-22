import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Role } from '../../../models/auth.models';

type Step = 1 | 2 | 3 | 4;

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent implements OnInit, OnDestroy {

  currentStep: Step = 1;
  selectedRole: Role | null = null;
  showPassword = false;
  showConfirmPassword = false;
  isLoading = false;
  passwordStrength = 0;
  registrationForm!: FormGroup;
  errorMessage = '';
  resendLoading = false;
  resendSuccess = false;

  confirmedUser = {
    name: '', email: '', role: '', initials: '',
    date: new Date().toLocaleDateString('fr-FR', {
      day: 'numeric', month: 'long', year: 'numeric'
    })
  };

  steps = [
    { num: 1, label: 'Profil' },
    { num: 2, label: 'Informations' },
    { num: 3, label: 'Vérification' },
    { num: 4, label: 'Confirmation' },
  ];

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.registrationForm = this.fb.group({
      prenom:          ['', [Validators.required, Validators.minLength(2)]],
      nom:             ['', [Validators.required, Validators.minLength(2)]],
      email:           ['', [Validators.required, Validators.email]],
      telephone:       [''],
      password:        ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
      acceptTerms:     [false, Validators.requiredTrue],
    }, { validators: this.passwordMatchValidator });
  }

  ngOnDestroy(): void {}

  passwordMatchValidator(g: AbstractControl) {
    return g.get('password')?.value === g.get('confirmPassword')?.value
      ? null : { mismatch: true };
  }

  selectRole(role: Role) { this.selectedRole = role; }

  goToStep(step: Step) {
    this.currentStep = step;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  nextStep() {
    this.errorMessage = '';

    // ── Étape 1 : choix du rôle ──────────────────────────────────
    if (this.currentStep === 1) {
      if (!this.selectedRole) return;
      this.goToStep(2);

    // ── Étape 2 : formulaire → appel API register ────────────────
    } else if (this.currentStep === 2) {
      if (this.registrationForm.invalid) {
        this.registrationForm.markAllAsTouched();
        return;
      }
      this.isLoading = true;
      const v = this.registrationForm.value;

      this.authService.register({
        prenom:    v.prenom,
        nom:       v.nom,
        email:     v.email,
        telephone: v.telephone,
        password:  v.password,
        role:      this.selectedRole!
      }).subscribe({
        next: () => {
          this.isLoading = false;
          // Passer à l'étape 3 : page "Vérifiez votre email"
          this.goToStep(3);
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err.error?.message || 'Une erreur est survenue';
        }
      });
    }
    // NOTE: Il n'y a plus d'étape 3 avec OTP.
    // La vérification se fait via le lien cliqué dans l'email.
    // Après clic sur le lien → redirection vers /login?verified=true
  }

  // ── Renvoyer le lien de vérification ────────────────────────────
  resendVerificationLink() {
    const email = this.registrationForm.get('email')?.value;
    if (!email) return;
    this.resendLoading = true;
    this.resendSuccess = false;
    this.errorMessage = '';

    this.authService.resendOtp(email).subscribe({
      next: () => {
        this.resendLoading = false;
        this.resendSuccess = true;
        // Cacher le message de succès après 4 secondes
        setTimeout(() => this.resendSuccess = false, 4000);
      },
      error: (err) => {
        this.resendLoading = false;
        this.errorMessage = err.error?.message || 'Erreur lors du renvoi';
      }
    });
  }

  // ── Navigation dashboard après confirmation ──────────────────────
  navigateToDashboard() {
    this.router.navigate(['/login']);
  }

  // ── Password strength ────────────────────────────────────────────
  onPasswordChange() {
    const val = this.registrationForm.get('password')?.value || '';
    let strength = 0;
    if (val.length >= 8)          strength++;
    if (/[A-Z]/.test(val))        strength++;
    if (/[0-9]/.test(val))        strength++;
    if (/[^A-Za-z0-9]/.test(val)) strength++;
    this.passwordStrength = strength;
  }

  get strengthLabel(): string {
    return ['', 'Faible', 'Moyen', 'Bon', 'Excellent'][this.passwordStrength];
  }

  get strengthColor(): string {
    return ['', '#c0392b', '#e67e22', '#4A7C7E', '#8B3A3A'][this.passwordStrength];
  }

  f(name: string) { return this.registrationForm.get(name); }

  isInvalid(name: string): boolean {
    const ctrl = this.f(name);
    return !!(ctrl?.invalid && ctrl?.touched);
  }

  private buildConfirmedUser() {
    const v = this.registrationForm.value;
    this.confirmedUser = {
      name:     `${v.prenom} ${v.nom}`,
      email:    v.email,
      role:     this.selectedRole === 'APPRENANT' ? 'Apprenant' : 'Formateur',
      initials: (v.prenom[0] + v.nom[0]).toUpperCase(),
      date:     new Date().toLocaleDateString('fr-FR', {
        day: 'numeric', month: 'long', year: 'numeric'
      })
    };
  }
}