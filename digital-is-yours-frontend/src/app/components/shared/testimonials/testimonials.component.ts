import { Component, AfterViewInit } from '@angular/core';

@Component({
  selector: 'app-testimonials',
  templateUrl: './testimonials.component.html',
  styleUrls: ['./testimonials.component.css']
})
export class TestimonialsComponent implements AfterViewInit {

  testimonials = [
    {
      name: 'Marie Dubois', role: 'Chef de Projet Marketing', initials: 'MD',
      avatarBg: 'linear-gradient(135deg,#8B3A3A,#a84848)',
      text: 'Cette formation a complètement transformé ma façon d\'aborder le marketing digital. Les contenus sont pertinents et les formateurs excellents !'
    },
    {
      name: 'Jean Dupont', role: 'Entrepreneur', initials: 'JD',
      avatarBg: 'linear-gradient(135deg,#4A7C7E,#5a9496)',
      text: 'J\'ai pu appliquer les techniques directement à mon entreprise. Les résultats sont impressionnants, je recommande vivement !'
    },
    {
      name: 'Yasmine Bouzid', role: 'Community Manager', initials: 'YB',
      avatarBg: 'linear-gradient(135deg,#9B8B6E,#b0a080)',
      text: 'La qualité des cours est exceptionnelle. J\'ai doublé mon engagement sur les réseaux sociaux en deux mois grâce à cette plateforme.'
    },
  ];

  ngAfterViewInit(): void {
    const io = new IntersectionObserver(entries => {
      entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
    }, { threshold: 0.08 });
    document.querySelectorAll('.reveal').forEach(el => io.observe(el));
  }
}