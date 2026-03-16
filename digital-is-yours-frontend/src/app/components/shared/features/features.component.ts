import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormationService, Formation, Categorie } from '../../../services/formation.service';

@Component({
  selector: 'app-features',
  templateUrl: './features.component.html',
  styleUrls: ['./features.component.css']
})
export class FeaturesComponent implements OnInit, AfterViewInit {

  formations: Formation[] = [];
  categories: Categorie[] = [];
  isLoading     = true;
  isLoadingCats = true;

  // ── Mode d'affichage ──────────────────────────────────
  // null = top 3 | 'all' = catalogue complet | number = catégorie
  modeAffichage: null | 'all' | number = null;

  // ── Recherche & Filtres (actifs uniquement en mode catalogue) ──
  recherche          = '';
  filtreNiveau       = '';
  filtreCategorie    = '';
  triPar             = 'popularite';

  // ── Pagination ────────────────────────────────────────
  page         = 1;
  parPage      = 6;

  // ── Getters ───────────────────────────────────────────

  get formationsFiltrees(): Formation[] {
    let list = [...this.formations];

    // En mode top 3, pas de filtre
    if (this.modeAffichage === null) return list.slice(0, 3);

    // Filtre catégorie (mode catégorie cliquée)
    if (typeof this.modeAffichage === 'number') {
      list = list.filter(f => f.categorieId === this.modeAffichage);
    }

    // Filtre catégorie via dropdown (mode 'all')
    if (this.modeAffichage === 'all' && this.filtreCategorie) {
      list = list.filter(f => f.categorieId === +this.filtreCategorie);
    }

    // Filtre niveau
    if (this.filtreNiveau) {
      list = list.filter(f => f.niveau === this.filtreNiveau);
    }

    // Recherche texte
    if (this.recherche.trim()) {
      const q = this.recherche.toLowerCase();
      list = list.filter(f =>
        f.titre?.toLowerCase().includes(q) ||
        f.description?.toLowerCase().includes(q)
      );
    }

    // Tri
    switch (this.triPar) {
      case 'titre':
        list.sort((a, b) => (a.titre || '').localeCompare(b.titre || ''));
        break;
      case 'duree_asc':
        list.sort((a, b) => (a.dureeEstimee || 0) - (b.dureeEstimee || 0));
        break;
      case 'duree_desc':
        list.sort((a, b) => (b.dureeEstimee || 0) - (a.dureeEstimee || 0));
        break;
      default: // popularite — ordre par défaut de l'API
        break;
    }

    return list;
  }

  get formationsPaginees(): Formation[] {
    if (this.modeAffichage === null) return this.formationsFiltrees;
    const debut = (this.page - 1) * this.parPage;
    return this.formationsFiltrees.slice(debut, debut + this.parPage);
  }

  get totalPages(): number {
    return Math.ceil(this.formationsFiltrees.length / this.parPage) || 1;
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  get categorieSelectionnee(): Categorie | null {
    if (typeof this.modeAffichage === 'number') {
      return this.categories.find(c => c.id === this.modeAffichage) || null;
    }
    return null;
  }

  get titreSection(): string {
    if (this.modeAffichage === 'all')           return 'Toutes les formations';
    if (typeof this.modeAffichage === 'number') return this.categorieSelectionnee?.nom || 'Formations';
    return 'Les plus suivies en ce moment';
  }

  get nbResultats(): number {
    return this.formationsFiltrees.length;
  }

  get afficherFiltres(): boolean {
    return this.modeAffichage !== null;
  }

  constructor(
    private formationService: FormationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.formationService.getFormationsPubliees().subscribe({
      next: (data) => {
        this.formations = data;
        this.isLoading  = false;
        setTimeout(() => this.initReveal(), 100);
      },
      error: () => { this.isLoading = false; }
    });

    this.formationService.getCategoriesVisibles().subscribe({
      next: (data) => {
        this.categories    = data;
        this.isLoadingCats = false;
        setTimeout(() => this.initReveal(), 100);
      },
      error: () => { this.isLoadingCats = false; }
    });
  }

  ngAfterViewInit(): void { this.initReveal(); }

  initReveal(): void {
    const io = new IntersectionObserver(entries => {
      entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
    }, { threshold: 0.07 });
    document.querySelectorAll('.reveal').forEach(el => io.observe(el));
  }

  // ── Actions navigation ────────────────────────────────

  voirCategorie(categorieId: number): void {
    this.modeAffichage   = categorieId;
    this.filtreCategorie = '';
    this.resetFiltres(false);
    this.scrollFormations();
  }

  voirToutCatalogue(): void {
    this.modeAffichage = 'all';
    this.resetFiltres(false);
    this.scrollFormations();
  }

  retourTop3(): void {
    this.modeAffichage = null;
    this.resetFiltres(true);
    this.scrollFormations();
  }

  reinitialiserCategorie(): void {
    this.modeAffichage = null;
    this.resetFiltres(true);
  }

 voirFormation(formationId: number): void {
    this.router.navigate(['/formation', formationId]);
}

  // ── Actions filtres ───────────────────────────────────

  onRecherche(): void      { this.page = 1; }
  onFiltreChange(): void   { this.page = 1; }
  onTriChange(): void      { this.page = 1; }

  resetFiltres(resetRecherche = true): void {
    if (resetRecherche) this.recherche = '';
    this.filtreNiveau    = '';
    this.filtreCategorie = '';
    this.triPar          = 'popularite';
    this.page            = 1;
  }

  goToPage(p: number): void {
    if (p < 1 || p > this.totalPages) return;
    this.page = p;
    this.scrollFormations();
  }

  private scrollFormations(): void {
    setTimeout(() => {
      const el = document.getElementById('formations-section');
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 50);
  }

  // ── Helpers ───────────────────────────────────────────

  getNbFormations(categorieId: number): number {
    return this.formations.filter(f => f.categorieId === categorieId).length;
  }

  getFormateurNom(f: Formation): string {
    return [f.formateurPrenom, f.formateurNom].filter(Boolean).join(' ') || 'Formateur à définir';
  }

  getDuree(heures: number): string {
    if (!heures || heures <= 0) return 'N/A';
    const h = Math.floor(heures);
    const m = Math.round((heures - h) * 60);
    if (h > 0 && m > 0) return `${h}h${m}`;
    if (h > 0)           return `${h}h`;
    return `${m}min`;
  }

  getAccentColor(niveau: string): string {
    const map: Record<string, string> = {
      'DEBUTANT':      '#8B3A3A',
      'INTERMEDIAIRE': '#4A7C7E',
      'AVANCE':        '#9B8B6E'
    };
    return map[niveau?.toUpperCase()] ?? '#8B3A3A';
  }

  getNiveauLabel(niveau: string): string {
    const map: Record<string, string> = {
      'DEBUTANT':      'Débutant',
      'INTERMEDIAIRE': 'Intermédiaire',
      'AVANCE':        'Avancé'
    };
    return map[niveau?.toUpperCase()] ?? niveau ?? '';
  }

  typeof_number(val: any): boolean {
    return typeof val === 'number';
  }
}