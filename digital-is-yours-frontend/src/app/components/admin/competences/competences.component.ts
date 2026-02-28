import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface Competence {
  id?: number;
  nom: string;
  categorie: string;
}

interface Formation {
  id: number;
  titre: string;
  categorieNom?: string;
  categorieCouleur?: string;
  niveau?: string;
  statut?: string;
}

@Component({
  selector: 'app-competences',
  templateUrl: './competences.component.html',
  styleUrls: ['./competences.component.css']
})
export class CompetencesComponent implements OnInit {

  private apiComp = 'http://localhost:8080/api/admin/competences';
  private apiForm = 'http://localhost:8080/api/admin/formations';
  private apiCat  = 'http://localhost:8080/api/admin/categories'; // ← AJOUT

  competences: Competence[] = [];
  formations:  Formation[]  = [];
  categories:  string[]     = [];

  searchComp = '';
  filterCat  = 'ALL';
  isLoading  = false;
  isSaving   = false;

  showCompModal  = false;
  compModalMode: 'create' | 'edit' = 'create';
  compForm: Competence = this.emptyComp();
  editingCompId: number | null = null;
  compError   = '';
  newCatInput = '';
  showNewCat  = false;

  showAssocModal     = false;
  selectedFormation: Formation | null = null;
  assocSearch        = '';
  assocFilterCat     = 'ALL';
  selectedIds: Set<number> = new Set();
  isSavingAssoc      = false;

  toast: { msg: string; type: 'success' | 'error' } | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit() { this.loadAll(); }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => this.toast = null, 4000);
  }

  loadAll() {
    this.isLoading = true;

    // 1. Charger les compétences
    this.http.get<Competence[]>(this.apiComp, { headers: this.headers() }).subscribe({
      next: d => {
        this.competences = d;
        this.isLoading   = false;
        this.loadCategories(); // ← charger les catégories après
      },
      error: () => {
        this.showToast('Erreur de chargement', 'error');
        this.isLoading = false;
      }
    });

    // 2. Charger les formations
    this.http.get<Formation[]>(this.apiForm, { headers: this.headers() }).subscribe({
      next: d => this.formations = d
    });
  }

  // ─── CORRECTION PRINCIPALE ───────────────────────────────────
  // Fusionner les catégories de formations + catégories des compétences existantes
  loadCategories() {
    // Catégories des compétences existantes (locales)
    const localCats = new Set(
      this.competences.map(c => c.categorie).filter(c => !!c && c.trim() !== '')
    );

    // Catégories des formations (table séparée avec couleur)
    // Ces catégories s'affichent dans le formulaire formation (image 2)
    // On les récupère aussi pour le formulaire compétence
    this.http.get<{ id: number; nom: string; couleur: string }[]>(
      this.apiCat, { headers: this.headers() }
    ).subscribe({
      next: formCats => {
        // Fusionner : catégories de formations + catégories des compétences existantes
        const allCats = new Set([
          ...formCats.map(c => c.nom),
          ...Array.from(localCats)
        ]);
        this.categories = Array.from(allCats).sort();
      },
      error: () => {
        // Fallback : uniquement les catégories locales des compétences
        this.categories = Array.from(localCats).sort();
      }
    });
  }

  get filteredCompetences(): Competence[] {
    return this.competences.filter(c => {
      const okQ = !this.searchComp || c.nom.toLowerCase().includes(this.searchComp.toLowerCase());
      const okC = this.filterCat === 'ALL' || c.categorie === this.filterCat;
      return okQ && okC;
    });
  }

  get filteredAssocComp(): Competence[] {
    return this.competences.filter(c => {
      const okQ = !this.assocSearch || c.nom.toLowerCase().includes(this.assocSearch.toLowerCase());
      const okC = this.assocFilterCat === 'ALL' || c.categorie === this.assocFilterCat;
      return okQ && okC;
    });
  }

  openCreateComp() {
    this.compModalMode = 'create';
    this.compForm      = this.emptyComp();
    this.editingCompId = null;
    this.compError     = '';
    this.newCatInput   = '';
    this.showNewCat    = false;
    this.showCompModal = true;
  }

  openEditComp(c: Competence) {
    this.compModalMode = 'edit';
    this.compForm      = { ...c };
    this.editingCompId = c.id!;
    this.compError     = '';
    this.newCatInput   = '';
    this.showNewCat    = false;
    this.showCompModal = true;
  }

  closeCompModal() { this.showCompModal = false; }

  saveCompetence() {
    this.compError = '';
    if (!this.compForm.nom?.trim()) { this.compError = 'Le nom est obligatoire'; return; }
    if (this.showNewCat && this.newCatInput.trim()) this.compForm.categorie = this.newCatInput.trim();
    this.isSaving = true;
    const url    = this.compModalMode === 'create' ? this.apiComp : `${this.apiComp}/${this.editingCompId}`;
    const method = this.compModalMode === 'create' ? 'post' : 'put';
    (this.http as any)[method](url, this.compForm, { headers: this.headers() }).subscribe({
      next: (res: any) => {
        this.showToast(res.message || 'Compétence sauvegardée ✓');
        this.isSaving = false;
        this.closeCompModal();
        this.loadAll();
      },
      error: (err: any) => {
        this.compError = err.error?.message || 'Erreur';
        this.isSaving  = false;
      }
    });
  }

  deleteCompetence(c: Competence) {
    if (!confirm(`Supprimer "${c.nom}" ?`)) return;
    this.http.delete<any>(`${this.apiComp}/${c.id}`, { headers: this.headers() }).subscribe({
      next: r => { this.showToast(r.message || 'Supprimée'); this.loadAll(); },
      error: e => this.showToast(e.error?.message || 'Erreur', 'error')
    });
  }

  openAssocModal(f: Formation) {
    this.selectedFormation = f;
    this.assocSearch       = '';
    this.assocFilterCat    = 'ALL';
    this.selectedIds       = new Set();
    this.http.get<Competence[]>(`${this.apiComp}/formation/${f.id}`, { headers: this.headers() }).subscribe({
      next: existing => {
        this.selectedIds    = new Set(existing.map(c => c.id!));
        this.showAssocModal = true;
      },
      error: () => {
        this.selectedIds    = new Set();
        this.showAssocModal = true;
      }
    });
  }

  closeAssocModal() { this.showAssocModal = false; this.selectedFormation = null; }

  toggleSelection(id: number) {
    this.selectedIds.has(id) ? this.selectedIds.delete(id) : this.selectedIds.add(id);
  }

  isSelected(id: number): boolean { return this.selectedIds.has(id); }
  selectAll()  { this.filteredAssocComp.forEach(c => this.selectedIds.add(c.id!)); }
  clearAll()   { this.selectedIds.clear(); }

  saveAssociation() {
    if (!this.selectedFormation) return;
    this.isSavingAssoc = true;
    this.http.put<any>(
      `${this.apiComp}/formation/${this.selectedFormation.id}`,
      { competenceIds: Array.from(this.selectedIds) },
      { headers: this.headers() }
    ).subscribe({
      next: r => {
        this.showToast(r.message || 'Association enregistrée ✓');
        this.isSavingAssoc = false;
        this.closeAssocModal();
      },
      error: e => {
        this.showToast(e.error?.message || 'Erreur', 'error');
        this.isSavingAssoc = false;
      }
    });
  }

  getInitiales(nom: string): string {
    return nom.split(' ').map(w => w[0] || '').join('').toUpperCase().slice(0, 2);
  }

  getCatColor(cat: string): string {
    const map: { [k: string]: string } = {
      'Marketing': '#8B3A3A', 'Technique': '#4A7C7E', 'Design': '#9B8B6E',
      'Management': '#3498db', 'Communication': '#9b59b6', 'Data': '#27ae60',
    };
    return map[cat] || '#6B5F52';
  }

  getCatBg(cat: string): string { return this.getCatColor(cat) + '1A'; }

  private emptyComp(): Competence { return { nom: '', categorie: '' }; }
}