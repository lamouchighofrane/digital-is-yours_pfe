import { Component, AfterViewInit, QueryList, ViewChildren, ElementRef } from '@angular/core';

@Component({
  selector: 'app-stats',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.css']
})
export class StatsComponent implements AfterViewInit {
  @ViewChildren('counterEl') counterEls!: QueryList<ElementRef>;

  stats = [
    { target: 500, suffix: '+', label: 'Apprenants actifs',      color: '#8B3A3A', gradEnd: '#c87c7c' },
    { target: 50,  suffix: '+', label: 'Formations disponibles', color: '#4A7C7E', gradEnd: '#7ab5b7' },
    { target: 20,  suffix: '+', label: 'Formateurs experts',     color: '#9B8B6E', gradEnd: '#c4b090' },
    { target: 95,  suffix: '%', label: 'Taux de satisfaction',   color: '#8B3A3A', gradEnd: '#c87c7c' },
  ];

  features = [
    { title: 'Suivi de progression',   desc: 'Tableau de bord intuitif avec analyses détaillées en temps réel.',                           color: '#8B3A3A' },
    { title: 'Certificats reconnus',   desc: 'Certificats professionnels validant vos compétences, partageables sur LinkedIn.',             color: '#4A7C7E' },
    { title: 'Calendrier intelligent', desc: 'Planifiez vos sessions d\'apprentissage avec des rappels automatiques personnalisés.',        color: '#9B8B6E' },
  ];

  ngAfterViewInit(): void {
    // Reveal observer
    const revealIO = new IntersectionObserver(entries => {
      entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
    }, { threshold: 0.08 });
    document.querySelectorAll('.reveal').forEach(el => revealIO.observe(el));

    // Counter observer — attend que les éléments soient visibles
    const counterIO = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) {
        // Petit délai pour s'assurer que le DOM est prêt
        setTimeout(() => {
          this.counterEls.forEach(ref => this.animate(ref.nativeElement));
        }, 200);
        counterIO.disconnect();
      }
    }, { threshold: 0.3 });

    setTimeout(() => {
      const statsSection = document.querySelector('app-stats section');
      if (statsSection) counterIO.observe(statsSection);
    }, 500);
  }

  private animate(el: HTMLElement): void {
    const target  = parseInt(el.dataset['target'] || '0');
    const suffix  = el.dataset['suffix'] || '';
    let step = 0;
    const steps   = 60;
    const duration = 2000;
    const interval = duration / steps;

    const timer = setInterval(() => {
      step++;
      const ease = 1 - Math.pow(1 - step / steps, 3);
      el.textContent = Math.floor(target * ease) + suffix;
      if (step >= steps) {
        clearInterval(timer);
        el.textContent = target + suffix;
      }
    }, interval);
  }
}