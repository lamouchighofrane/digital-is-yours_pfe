import { Component, AfterViewInit } from '@angular/core';

@Component({
  selector: 'app-features',
  templateUrl: './features.component.html',
  styleUrls: ['./features.component.css']
})
export class FeaturesComponent implements AfterViewInit {

  categories = [
    {
      title: 'Marketing Digital',
      desc: 'SEO, Google Ads, réseaux sociaux, e-mail marketing et stratégies de croissance.',
      count: 12,
      accentColor: '#8B3A3A',
      accentBg: '#f9f0f0',
      tag: 'Populaire',
      img: 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=400&h=240&fit=crop&auto=format'
    },
    {
      title: 'Neuro Marketing',
      desc: 'Psychologie du consommateur, biais cognitifs et techniques de persuasion avancées.',
      count: 8,
      accentColor: '#4A7C7E',
      accentBg: '#edf4f4',
      tag: 'Expert',
      img: 'https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=400&h=240&fit=crop&auto=format'
    },
    {
      title: 'Marketing Émotionnel',
      desc: 'Storytelling, branding émotionnel et création de connexions durables avec votre audience.',
      count: 10,
      accentColor: '#9B8B6E',
      accentBg: '#f5f2ed',
      tag: 'Tendance',
      img: 'https://images.unsplash.com/photo-1552664730-d307ca884978?w=400&h=240&fit=crop&auto=format'
    },
    {
      title: 'Soft Skills',
      desc: 'Leadership, communication, gestion du temps et intelligence émotionnelle professionnelle.',
      count: 15,
      accentColor: '#8B3A3A',
      accentBg: '#f9f0f0',
      tag: 'Essentiel',
      img: 'https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=400&h=240&fit=crop&auto=format'
    },
  ];

  formations = [
    {
      category: 'Marketing Digital',
      title: 'Maîtriser Google Ads de A à Z',
      instructor: 'Sophie Moreau',
      level: 'Débutant',
      duration: '10h 30min',
      lessons: '42 leçons',
      students: '1 240',
      rating: '4.8',
      reviews: '128',
      accentColor: '#8B3A3A',
      img: 'https://images.unsplash.com/photo-1432888498266-38ffec3eaf0a?w=600&h=340&fit=crop&auto=format'
    },
    {
      category: 'Neuro Marketing',
      title: 'Psychologie & Décisions d\'Achat',
      instructor: 'Dr. Karim Mansouri',
      level: 'Intermédiaire',
      duration: '15h 20min',
      lessons: '58 leçons',
      students: '980',
      rating: '4.9',
      reviews: '204',
      accentColor: '#4A7C7E',
      img: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&h=340&fit=crop&auto=format'
    },
    {
      category: 'Soft Skills',
      title: 'Communication Professionnelle Efficace',
      instructor: 'Leila Ben Ali',
      level: 'Tous niveaux',
      duration: '8h 45min',
      lessons: '35 leçons',
      students: '2 100',
      rating: '4.7',
      reviews: '312',
      accentColor: '#9B8B6E',
      img: 'https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=600&h=340&fit=crop&auto=format'
    },
  ];

  ngAfterViewInit(): void {
    const io = new IntersectionObserver(entries => {
      entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
    }, { threshold: 0.07 });
    document.querySelectorAll('.reveal').forEach(el => io.observe(el));
  }
}