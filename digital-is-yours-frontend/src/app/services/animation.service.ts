import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class AnimationService {
  private observer!: IntersectionObserver;

  constructor() {
    this.initObserver();
  }

  private initObserver(): void {
    const options: IntersectionObserverInit = {
      root: null,
      rootMargin: '0px',
      threshold: 0.1
    };

    this.observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          // Optionnel: arrêter d'observer après l'animation
          // this.observer.unobserve(entry.target);
        }
      });
    }, options);
  }

  observe(element: Element): void {
    if (this.observer && element) {
      this.observer.observe(element);
    }
  }

  unobserve(element: Element): void {
    if (this.observer && element) {
      this.observer.unobserve(element);
    }
  }

  disconnect(): void {
    if (this.observer) {
      this.observer.disconnect();
    }
  }
}