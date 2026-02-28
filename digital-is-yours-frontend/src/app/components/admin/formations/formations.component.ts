import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface Categorie {
  id: number;
  nom: string;
  couleur: string;
}

interface Competence {
  id: number;
  nom: string;
  categorie: string;
}

interface Formateur {
  id: number;
  prenom: string;
  nom: string;
  email: string;
  active: boolean;
}

interface Formation {
  id?: number;
  titre: string;
  description: string;
  objectifsApprentissage: string;
  prerequis: string;
  pourQui: string;
  imageCouverture?: string;
  dureeEstimee: number;
  niveau: string;
  statut: string;
  categorieId?: number | null;
  categorieNom?: string;
  categorieCouleur?: string;
  formateurId?: number | null;
  formateurNom?: string;
  formateurPrenom?: string;
  formateurEmail?: string;
  dateCreation?: string;
  datePublication?: string;
  nombreInscrits?: number;
  nombreCertifies?: number;
  noteMoyenne?: number;
  tauxReussite?: number;
}

@Component({
  selector: 'app-formations',
  templateUrl: './formations.component.html',
  styleUrls: ['./formations.component.css']
})
export class FormationsComponent implements OnInit {

  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  private apiUrl        = 'http://localhost:8080/api/admin/formations';
  private categoriesUrl = 'http://localhost:8080/api/admin/categories';
  private apiComp       = 'http://localhost:8080/api/admin/competences';

  formations:  Formation[]  = [];
  categories:  Categorie[]  = [];
  formateurs:  Formateur[]  = [];
  competencesDispo: Competence[] = [];
  competencesSelectionnees: number[] = [];

  searchTerm   = '';
  filterStatut = 'ALL';
  filterCat    = 'ALL';

  isLoading = false;
  isSaving  = false;

  showModal  = false;
  modalMode: 'create' | 'edit' = 'create';
  form: Formation = this.emptyForm();
  editingId: number | null = null;

  // ★★★ Modal affectation formateur ★★★
  showFormateurModal = false;
  selectedFormationForFormateur: Formation | null = null;
  searchFormateur = '';

  imagePreview: string | null = null;
  isDragOver   = false;
  selectedFile: File | null = null;

  activeTab: 'info' | 'details' = 'info';

  stats = { total: 0, publiees: 0, brouillons: 0 };

  toast: { msg: string; type: 'success' | 'error' } | null = null;

  niveaux = [
    { value: 'DEBUTANT',      label: 'Débutant',      color: '#27ae60', bg: '#e8f5e9' },
    { value: 'INTERMEDIAIRE', label: 'Intermédiaire', color: '#e67e22', bg: '#fff3cd' },
    { value: 'AVANCE',        label: 'Avancé',        color: '#8B3A3A', bg: '#fce4e4' }
  ];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadFormations();
    this.loadCategories();
    this.loadFormateurs();
    this.loadStats();
    this.loadCompetences();
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => this.toast = null, 4000);
  }

  // ── Chargement ────────────────────────────────────────────
  loadFormations() {
    this.isLoading = true;
    this.http.get<Formation[]>(this.apiUrl, { headers: this.headers() }).subscribe({
      next: d => { this.formations = d; this.isLoading = false; },
      error: () => { this.showToast('Erreur de chargement', 'error'); this.isLoading = false; }
    });
  }

  loadCategories() {
    this.http.get<Categorie[]>(this.categoriesUrl, { headers: this.headers() })
      .subscribe({ next: d => this.categories = d });
  }

  loadFormateurs() {
    this.http.get<Formateur[]>(`${this.apiUrl}/formateurs`, { headers: this.headers() })
      .subscribe({ next: d => this.formateurs = d });
  }

  loadStats() {
    this.http.get<any>(`${this.apiUrl}/stats`, { headers: this.headers() })
      .subscribe({ next: d => this.stats = d });
  }

  loadCompetences() {
    this.http.get<Competence[]>(this.apiComp, { headers: this.headers() }).subscribe({
      next: d => this.competencesDispo = d
    });
  }

  // ── Filtrage ──────────────────────────────────────────────
  get filteredFormations(): Formation[] {
    return this.formations.filter(f => {
      const okS = this.filterStatut === 'ALL' || f.statut === this.filterStatut;
      const okC = this.filterCat === 'ALL' || String(f.categorieId) === this.filterCat;
      const okQ = !this.searchTerm ||
        f.titre.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        (f.categorieNom || '').toLowerCase().includes(this.searchTerm.toLowerCase());
      return okS && okC && okQ;
    });
  }

  get filteredFormateurs(): Formateur[] {
    if (!this.searchFormateur) return this.formateurs;
    const t = this.searchFormateur.toLowerCase();
    return this.formateurs.filter(f =>
      f.prenom.toLowerCase().includes(t) ||
      f.nom.toLowerCase().includes(t) ||
      f.email.toLowerCase().includes(t)
    );
  }

  // ── Modal Formation ───────────────────────────────────────
  openCreateModal() {
    this.modalMode               = 'create';
    this.form                    = this.emptyForm();
    this.editingId               = null;
    this.imagePreview            = null;
    this.selectedFile            = null;
    this.activeTab               = 'info';
    this.competencesSelectionnees = [];
    this.showModal               = true;
  }

  openEditModal(f: Formation) {
    this.modalMode    = 'edit';
    this.form         = { ...f };
    this.editingId    = f.id!;
    this.imagePreview = f.imageCouverture || null;
    this.selectedFile = null;
    this.activeTab    = 'info';
    this.competencesSelectionnees = [];
    this.http.get<Competence[]>(`${this.apiComp}/formation/${f.id}`, { headers: this.headers() }).subscribe({
      next: comps => this.competencesSelectionnees = comps.map(c => c.id)
    });
    this.showModal = true;
  }

  closeModal() {
    this.showModal    = false;
    this.imagePreview = null;
    this.selectedFile = null;
    this.competencesSelectionnees = [];
  }

  // ── Modal Affectation Formateur ──────────────────────────
  openFormateurModal(f: Formation) {
    this.selectedFormationForFormateur = f;
    this.searchFormateur = '';
    this.showFormateurModal = true;
  }

  closeFormateurModal() {
    this.showFormateurModal = false;
    this.selectedFormationForFormateur = null;
    this.searchFormateur = '';
  }

  affecterFormateur(formateur: Formateur) {
    if (!this.selectedFormationForFormateur?.id) return;

    const formationId = this.selectedFormationForFormateur.id;

    this.http.patch<Formation>(
      `${this.apiUrl}/${formationId}/affecter-formateur`,
      { formateurId: formateur.id },
      { headers: this.headers() }
    ).subscribe({
      next: (formationMiseAJour) => {
        // ✅ Mettre à jour localement sans recharger toute la liste
        const idx = this.formations.findIndex(f => f.id === formationId);
        if (idx !== -1) {
          this.formations[idx] = {
            ...this.formations[idx],
            formateurId:     formateur.id,
            formateurPrenom: formateur.prenom,
            formateurNom:    formateur.nom,
            formateurEmail:  formateur.email
          };
          // Si le backend renvoie les données complètes, les utiliser directement
          if (formationMiseAJour?.formateurId) {
            this.formations[idx] = { ...this.formations[idx], ...formationMiseAJour };
          }
        }
        this.showToast(`${formateur.prenom} ${formateur.nom} affecté(e) ✓`);
        this.closeFormateurModal();
      },
      error: (err) => {
        console.error('Erreur affectation formateur:', err);
        const msg = err.error?.message || err.error?.error || `Erreur ${err.status}`;
        this.showToast(msg, 'error');
      }
    });
  }

  retirerFormateur(formation: Formation) {
    if (!formation?.id) return;
    if (!confirm('Retirer le formateur de cette formation ?')) return;

    const formationId = formation.id;

    // ✅ Utiliser DELETE ou PATCH selon ce que supporte votre backend
    this.http.delete<any>(
      `${this.apiUrl}/${formationId}/affecter-formateur`,
      { headers: this.headers() }
    ).subscribe({
      next: () => {
        // Mettre à jour localement
        const idx = this.formations.findIndex(f => f.id === formationId);
        if (idx !== -1) {
          this.formations[idx] = {
            ...this.formations[idx],
            formateurId:     null,
            formateurPrenom: undefined,
            formateurNom:    undefined,
            formateurEmail:  undefined
          };
        }
        this.showToast('Formateur retiré ✓');
        if (this.showFormateurModal) this.closeFormateurModal();
      },
      error: (err) => {
        // ✅ Fallback : essayer avec PATCH { formateurId: null } si DELETE ne marche pas
        console.warn('DELETE échoué, tentative PATCH avec formateurId: null', err);
        this.retirerFormateurViaPatch(formationId);
      }
    });
  }

  // ✅ Méthode fallback pour retirer via PATCH
  private retirerFormateurViaPatch(formationId: number) {
    this.http.patch<any>(
      `${this.apiUrl}/${formationId}/affecter-formateur`,
      { formateurId: null },
      { headers: this.headers() }
    ).subscribe({
      next: () => {
        const idx = this.formations.findIndex(f => f.id === formationId);
        if (idx !== -1) {
          this.formations[idx] = {
            ...this.formations[idx],
            formateurId:     null,
            formateurPrenom: undefined,
            formateurNom:    undefined,
            formateurEmail:  undefined
          };
        }
        this.showToast('Formateur retiré ✓');
        if (this.showFormateurModal) this.closeFormateurModal();
      },
      error: (err) => {
        const msg = err.error?.message || `Erreur ${err.status}`;
        this.showToast(msg, 'error');
      }
    });
  }

  getFormateurInitiales(f: Formateur): string {
    return ((f.prenom?.[0] || '') + (f.nom?.[0] || '')).toUpperCase() || '?';
  }

  // ── Compétences ───────────────────────────────────────────
  toggleCompetence(id: number) {
    const idx = this.competencesSelectionnees.indexOf(id);
    if (idx >= 0) this.competencesSelectionnees.splice(idx, 1);
    else this.competencesSelectionnees.push(id);
  }

  isCompSelected(id: number): boolean {
    return this.competencesSelectionnees.includes(id);
  }

  getCompNom(id: number): string {
    const c = this.competencesDispo.find(c => c.id === id);
    return c ? c.nom : '';
  }

  get competencesParCategorie(): { categorie: string; items: Competence[] }[] {
    const map = new Map<string, Competence[]>();
    this.competencesDispo.forEach(c => {
      const cat = c.categorie || 'Sans catégorie';
      if (!map.has(cat)) map.set(cat, []);
      map.get(cat)!.push(c);
    });
    return Array.from(map.entries()).map(([categorie, items]) => ({ categorie, items }));
  }

  // ── Sauvegarde Formation ──────────────────────────────────
  saveFormation() {
    if (!this.form.titre?.trim()) { this.showToast('Le titre est obligatoire', 'error'); return; }
    if (!this.form.niveau)        { this.showToast('Choisissez un niveau', 'error'); return; }
    this.isSaving = true;
    if (this.selectedFile) {
      this.toBase64(this.selectedFile).then(b64 => {
        this.form.imageCouverture = b64;
        this.doSave();
      });
    } else {
      this.doSave();
    }
  }

  private doSave() {
    const url    = this.modalMode === 'create' ? this.apiUrl : `${this.apiUrl}/${this.editingId}`;
    const method = this.modalMode === 'create' ? 'post' : 'put';
    (this.http as any)[method](url, this.form, { headers: this.headers() }).subscribe({
      next: (res: any) => {
        const formationId = res.id || res.data?.id || this.editingId;
        if (formationId) {
          this.http.put(
            `${this.apiComp}/formation/${formationId}`,
            { competenceIds: this.competencesSelectionnees },
            { headers: this.headers() }
          ).subscribe();
        }
        this.showToast(res.message || (this.modalMode === 'create' ? 'Formation créée ✓' : 'Formation modifiée ✓'));
        this.isSaving = false;
        this.closeModal();
        this.loadFormations();
        this.loadStats();
      },
      error: (err: any) => {
        this.showToast(err.error?.message || 'Erreur lors de la sauvegarde', 'error');
        this.isSaving = false;
      }
    });
  }

  toggleStatut(f: Formation) {
    this.http.patch<any>(`${this.apiUrl}/${f.id}/toggle-statut`, {}, { headers: this.headers() })
      .subscribe({
        next: r => { f.statut = r.statut; this.loadStats(); this.showToast(r.message); },
        error: e => this.showToast(e.error?.message || 'Erreur', 'error')
      });
  }

  deleteFormation(f: Formation) {
    if (!confirm(`Supprimer définitivement "${f.titre}" ?`)) return;
    this.http.delete<any>(`${this.apiUrl}/${f.id}`, { headers: this.headers() }).subscribe({
      next: r => {
        this.formations = this.formations.filter(x => x.id !== f.id);
        this.loadStats();
        this.showToast(r.message || 'Formation supprimée');
      },
      error: e => this.showToast(e.error?.message || 'Erreur', 'error')
    });
  }

  // ── Image ─────────────────────────────────────────────────
  triggerFileInput() { this.fileInputRef?.nativeElement.click(); }

  onFileSelected(e: Event) {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (f) this.processFile(f);
  }

  onDragOver(e: DragEvent) { e.preventDefault(); e.stopPropagation(); this.isDragOver = true; }

  onDrop(e: DragEvent) {
    e.preventDefault(); e.stopPropagation(); this.isDragOver = false;
    const f = e.dataTransfer?.files[0];
    if (f?.type.startsWith('image/')) this.processFile(f);
    else this.showToast('Fichier image uniquement', 'error');
  }

  private processFile(file: File) {
    if (file.size > 5 * 1024 * 1024) { this.showToast('Taille max : 5 MB', 'error'); return; }
    this.selectedFile = file;
    const r = new FileReader();
    r.onload = (e: any) => {
      this.imagePreview = e.target.result as string;
      this.form.imageCouverture = this.imagePreview;
    };
    r.readAsDataURL(file);
  }

  removeImage() {
    this.imagePreview = null;
    this.selectedFile = null;
    this.form.imageCouverture = '';
    if (this.fileInputRef?.nativeElement) this.fileInputRef.nativeElement.value = '';
  }

  onUrlInput() {
    if (this.form.imageCouverture?.startsWith('http')) {
      this.imagePreview = this.form.imageCouverture;
      this.selectedFile = null;
    }
  }

  private toBase64(file: File): Promise<string> {
    return new Promise((res, rej) => {
      const r = new FileReader();
      r.onload = e => res(e.target?.result as string);
      r.onerror = () => rej(new Error('Erreur lecture fichier'));
      r.readAsDataURL(file);
    });
  }

  // ── Helpers ───────────────────────────────────────────────
  getCoverStyle(f: Formation): { [k: string]: string } {
    if (f.imageCouverture) {
      return {
        'background-image': `url(${f.imageCouverture})`,
        'background-size': 'cover',
        'background-position': 'center'
      };
    }
    const color = f.categorieCouleur || '#8B3A3A';
    return { 'background': `linear-gradient(135deg, ${color}25 0%, ${color}55 100%)` };
  }

  getNiveauInfo(niveau: string) {
    return this.niveaux.find(n => n.value === niveau)
      || { label: niveau, color: '#6B5F52', bg: '#F5F1EB' };
  }

  getCategorieColor(catId?: number | null): string {
    const cat = this.categories.find(c => c.id === catId);
    return cat?.couleur || '#8B3A3A';
  }

  getCategorieNom(catId?: number | null): string {
    if (!catId) return 'Sans catégorie';
    const found = this.categories.find(c => c.id === catId);
    return found ? found.nom : 'Sans catégorie';
  }

  private emptyForm(): Formation {
    return {
      titre: '', description: '', objectifsApprentissage: '',
      prerequis: '', pourQui: '', imageCouverture: '',
      dureeEstimee: 1, niveau: 'DEBUTANT', statut: 'BROUILLON',
      categorieId: null, formateurId: null
    };
  }
}