import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Role } from '../../../models/auth.models';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  loginForm: FormGroup;
  selectedRole: Role = 'APPRENANT';
  showPassword = false;
  isLoading = false;
  loginError = false;
  errorMessage = '';
  errorType: 'generic' | 'desactive' | 'non-verifie' = 'generic';
  successMessage = '';

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {
    this.loginForm = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      remember: [false]
    });
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['verified'] === 'true') {
        this.successMessage = '✅ Email vérifié ! Vous pouvez maintenant vous connecter.';
      }
    });
  }

  selectRole(role: Role): void {
    this.selectedRole = role;
  }

  isInvalid(field: string): boolean {
    const ctrl = this.loginForm.get(field);
    return !!(ctrl && ctrl.invalid && ctrl.touched);
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.loginError = false;
    this.successMessage = '';

    const { email, password } = this.loginForm.value;

    this.authService.login({ email, password, role: this.selectedRole }).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.role === 'FORMATEUR') {
          this.router.navigate(['/dashboard-formateur']);
        } else {
          this.router.navigate(['/dashboard-apprenant']);
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.loginError = true;

        // Récupérer le message d'erreur du backend
        const msg: string = err.error?.message || '';

        if (msg === 'COMPTE_DESACTIVE' || err.status === 403) {
          this.errorType = 'desactive';
          this.errorMessage = "Votre compte a été désactivé par un administrateur. Contactez le support.";
        } else if (msg.includes('vérifier votre email') || msg === 'EMAIL_NON_VERIFIE') {
          this.errorType = 'non-verifie';
          this.errorMessage = "Votre email n'est pas encore vérifié. Vérifiez votre boîte mail.";
        } else {
          this.errorType = 'generic';
          this.errorMessage = msg || 'Email ou mot de passe incorrect.';
        }
      }
    });
  }

  goToRegister(): void { this.router.navigate(['/register']); }
  goToForgotPassword(): void { this.router.navigate(['/forgot-password']); }
  goToHome(): void { this.router.navigate(['/home']); }
}