import { Component, HostListener, OnInit } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit {
  isScrolled      = false;
  mobileMenuOpen  = false;
  isConnected     = false;
  userName        = '';
  userPhoto       = '';
  userInitiales   = '';

  navLinks = [
    { label: 'Accueil',  href: '/'        },
    { label: 'À propos', href: '#about'   },
    { label: 'Contact',  href: '#contact' },
  ];

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.checkConnexion();
  }

  checkConnexion(): void {
    const token = localStorage.getItem('token');
    const user  = localStorage.getItem('user');
    if (token && user) {
      try {
        const u = JSON.parse(user);
        this.isConnected   = true;
        this.userName      = u.prenom || u.nom || 'Mon espace';
        this.userPhoto     = u.photo  || '';
        const p = (u.prenom || '')[0] || '';
        const n = (u.nom    || '')[0] || '';
        this.userInitiales = (p + n).toUpperCase() || '?';
      } catch {
        this.isConnected = false;
      }
    } else {
      this.isConnected = false;
    }
  }

  @HostListener('window:scroll')
  onScroll() { this.isScrolled = window.scrollY > 50; }

  toggleMobileMenu() { this.mobileMenuOpen = !this.mobileMenuOpen; }

  navigateToCatalogue() {
    // Redirige connecté → dashboard, visiteur → /catalogue
    if (this.isConnected) {
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      const role = user.role || '';
      if (role === 'APPRENANT')  this.router.navigate(['/apprenant/dashboard']);
      else if (role === 'FORMATEUR') this.router.navigate(['/formateur/dashboard']);
      else this.router.navigate(['/catalogue']);
    } else {
      this.router.navigate(['/catalogue']);
    }
  }

  navigateToDashboard() {
    const user = JSON.parse(localStorage.getItem('user') || '{}');
    const role = user.role || '';
    if (role === 'APPRENANT')      this.router.navigate(['/apprenant/dashboard']);
    else if (role === 'FORMATEUR') this.router.navigate(['/formateur/dashboard']);
    else                           this.router.navigate(['/login']);
  }

  navigateToLogin()    { this.router.navigate(['/login']);    }
  navigateToRegister() { this.router.navigate(['/register']); }

  logout() {
    localStorage.clear();
    this.isConnected = false;
    this.router.navigate(['/']);
  }
}