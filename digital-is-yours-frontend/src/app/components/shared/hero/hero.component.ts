import { Component } from '@angular/core';

@Component({
  selector: 'app-hero',
  templateUrl: './hero.component.html',
  styleUrls: ['./hero.component.css']
})
export class HeroComponent {

  tickerItems = [
    'Marketing Digital','Neuro Marketing','Marketing Émotionnel','Soft Skills',
    'Analytics','Branding','Social Media','Growth Hacking','Content Marketing','Email Marketing'
  ];
  doubledTickerItems = [...this.tickerItems, ...this.tickerItems];

  highlights = [
    { value: '500+', label: 'Apprenants' },
    { value: '50+',  label: 'Formations' },
    { value: '95%',  label: 'Satisfaction' },
  ];

  navigateToCatalogue() { console.log('→ catalogue'); }
}