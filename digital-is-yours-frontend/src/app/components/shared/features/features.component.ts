import { Component, OnInit, AfterViewInit } from '@angular/core';
import { FormationService, Formation, Categorie } from '../../../services/formation.service';

@Component({
  selector: 'app-features',
  templateUrl: './features.component.html',
  styleUrls: ['./features.component.css']
})
export class FeaturesComponent implements OnInit, AfterViewInit {

  formations: Formation[] = [];
  categories: Categorie[] = [];
  isLoading = true;
  isLoadingCats = true;

  constructor(private formationService: FormationService) {}

  ngOnInit(): void {
    this.formationService.getFormationsPubliees().subscribe({
      next: (data) => {
        this.formations = data;
        this.isLoading = false;
        setTimeout(() => this.initReveal(), 100);
      },
      error: () => {
        this.isLoading = false;
      }
    });

    this.formationService.getCategoriesVisibles().subscribe({
      next: (data) => {
        this.categories = data;
        this.isLoadingCats = false;
        setTimeout(() => this.initReveal(), 100);
      },
      error: () => {
        this.isLoadingCats = false;
      }
    });
  }

  initReveal(): void {
    const io = new IntersectionObserver(entries => {
      entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
    }, { threshold: 0.07 });
    document.querySelectorAll('.reveal').forEach(el => io.observe(el));
  }

  ngAfterViewInit(): void {
    this.initReveal();
  }

  getNbFormations(categorieId: number): number {
    return this.formations.filter(f => f.categorieId === categorieId).length;
  }

  getFormateurNom(f: Formation): string {
    return [f.formateurPrenom, f.formateurNom].filter(Boolean).join(' ') || 'Formateur à définir';
  }

  getDuree(minutes: number): string {
    if (!minutes) return 'N/A';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}h${m > 0 ? m + 'min' : ''}` : `${m}min`;
  }

  getAccentColor(niveau: string): string {
    const map: Record<string, string> = {
      'DEBUTANT': '#8B3A3A',
      'INTERMEDIAIRE': '#4A7C7E',
      'AVANCE': '#9B8B6E'
    };
    return map[niveau?.toUpperCase()] ?? '#8B3A3A';
  }
}