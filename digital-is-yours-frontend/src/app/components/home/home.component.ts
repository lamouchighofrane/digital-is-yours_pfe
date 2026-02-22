import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {

  adminKeyBuffer = '';
  showAdminModal = false;
  private keydownListener: any;

  constructor(private router: Router) {}

  ngOnInit(): void {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    this.keydownListener = (e: KeyboardEvent) => this.onKeydown(e);
    document.addEventListener('keydown', this.keydownListener);
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown', this.keydownListener);
  }

  onKeydown(event: KeyboardEvent) {
    this.adminKeyBuffer += event.key.toLowerCase();
    if (this.adminKeyBuffer.length > 5) {
      this.adminKeyBuffer = this.adminKeyBuffer.slice(-5);
    }
    if (this.adminKeyBuffer === 'admin') {
      this.showAdminModal = true;
      this.adminKeyBuffer = '';
    }
  }

  closeAdminModal() {
    this.showAdminModal = false;
  }

  goToAdminLogin() {
    this.showAdminModal = false;
    this.router.navigate(['/admin-login']);
  }
}