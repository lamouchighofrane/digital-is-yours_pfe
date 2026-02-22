import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.css']
})
export class FooterComponent {
  currentYear = new Date().getFullYear();

  quickLinks = [
    { name: 'Accueil',   link: '/' },
    { name: 'Catalogue', link: '/catalogue' },
    { name: 'À propos',  link: '#about' },
    { name: 'Contact',   link: '#contact' },
  ];

  categories = [
    { name: 'Marketing Digital',    link: '#' },
    { name: 'Neuro Marketing',      link: '#' },
    { name: 'Marketing Émotionnel', link: '#' },
    { name: 'Soft Skills',          link: '#' },
  ];

  legal = [
    { name: 'Mentions légales',             link: '/legal' },
    { name: 'Confidentialité',              link: '/privacy' },
    { name: 'Conditions d\'utilisation',    link: '/terms' },
  ];
}