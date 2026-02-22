import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-verify-email',
  templateUrl: './verifyEmail.component.html',   
  styleUrls: ['./verifyEmail.component.css']     
})
export class VerifyEmailComponent implements OnInit {

  status: 'loading' | 'success' | 'error' | 'already-used' = 'loading';
  message = '';
  countdown = 5;
  private timer: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.status = 'error';
      this.message = 'Lien invalide.';
      return;
    }

    this.http.get(`http://localhost:8080/api/auth/verify-email?token=${token}`)
      .subscribe({
        next: (res: any) => {
          this.status = 'success';
          this.startCountdown();
        },
        error: (err) => {
          if (err.error?.message?.includes('expiré')) {
            this.status = 'error';
            this.message = 'Ce lien a expiré. Connectez-vous pour en recevoir un nouveau.';
          } else if (err.error?.message?.includes('déjà')) {
            this.status = 'already-used';
            this.message = 'Votre compte est déjà vérifié !';
            this.startCountdown();
          } else {
            this.status = 'error';
            this.message = 'Lien invalide ou expiré.';
          }
        }
      });
  }

  startCountdown(): void {
    this.timer = setInterval(() => {
      this.countdown--;
      if (this.countdown <= 0) {
        clearInterval(this.timer);
        this.router.navigate(['/login'], {
          queryParams: this.status === 'success' ? { verified: 'true' } : {}
        });
      }
    }, 1000);
  }

  goToLogin(): void {
    clearInterval(this.timer);
    this.router.navigate(['/login'], {
      queryParams: this.status === 'success' ? { verified: 'true' } : {}
    });
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }
}