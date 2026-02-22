import { Component, HostListener } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent {
  isScrolled = false;
  mobileMenuOpen = false;

  navLinks = [
    { label: 'Accueil',  href: '/' },
    { label: 'À propos', href: '#about' },
    { label: 'Contact',  href: '#contact' },
  ];

  constructor(private router: Router) {}

  @HostListener('window:scroll')
  onScroll() { this.isScrolled = window.scrollY > 50; }

  toggleMobileMenu() { this.mobileMenuOpen = !this.mobileMenuOpen; }
  navigateToCatalogue() { console.log('→ catalogue'); }
  navigateToLogin()     { this.router.navigate(['/login']); }
  navigateToRegister()  { this.router.navigate(['/register']); }
}