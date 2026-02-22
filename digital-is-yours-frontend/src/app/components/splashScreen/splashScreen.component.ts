import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-splash-screen',
  templateUrl: './splashScreen.component.html',   
  styleUrls: ['./splashScreen.component.css']     
})
export class SplashScreenComponent implements OnInit, OnDestroy {

  particles: { x: number; y: number; size: number; delay: number; duration: number; opacity: number }[] = [];
  letters = ['D', 'i', 'g', 'i', 't', 'a', 'l', ' ', 'I', 's', ' ', 'Y', 'o', 'u', 'r', 's'];
  isLeaving = false;
  private timer: any;

  constructor(private router: Router) {}

  ngOnInit(): void {
    // Générer les particules
    for (let i = 0; i < 60; i++) {
      this.particles.push({
        x: Math.random() * 100,
        y: Math.random() * 100,
        size: Math.random() * 4 + 1,
        delay: Math.random() * 3,
        duration: Math.random() * 4 + 3,
        opacity: Math.random() * 0.6 + 0.2
      });
    }

    // Transition après 3.2 secondes
    this.timer = setTimeout(() => {
      this.isLeaving = true;
      setTimeout(() => {
        this.router.navigate(['/home']);
      }, 800);
    }, 3200);
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
  }

  skip(): void {
    clearTimeout(this.timer);
    this.isLeaving = true;
    setTimeout(() => this.router.navigate(['/home']), 500);
  }
}