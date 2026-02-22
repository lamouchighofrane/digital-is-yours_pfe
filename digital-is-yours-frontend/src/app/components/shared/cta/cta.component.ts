import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-cta',
  templateUrl: './cta.component.html',
  styleUrls: ['./cta.component.css']
})
export class CtaComponent {
  constructor(private router: Router) {}
  navigateToCatalogue() { console.log('→ catalogue'); }
  navigateToRegister()  { this.router.navigate(['/register']); }
}