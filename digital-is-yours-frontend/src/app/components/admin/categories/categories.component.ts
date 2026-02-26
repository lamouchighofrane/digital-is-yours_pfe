import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { DomSanitizer, SafeStyle } from '@angular/platform-browser';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface Categorie {
  id?: number;
  nom: string;
  description: string;
  couleur: string;
  imageCouverture?: string;
  metaDescription?: string;
  ordreAffichage: number;
  visibleCatalogue: boolean;
  dateCreation?: string;
}

@Component({
  selector: 'app-categories',
  templateUrl: './categories.component.html',
  styleUrls: ['./categories.component.css']
})
export class CategoriesComponent implements OnInit {

  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  private apiUrl = 'http://localhost:8080/api/admin/categories';

  categories: Categorie[] = [];
  searchTerm = '';
  showModal  = false;
  modalMode: 'create' | 'edit' = 'create';
  form: Categorie = this.emptyForm();
  isSaving   = false;
  imagePreview: string | null = null;
  isDragOver = false;
  selectedFile: File | null = null;

  toast: { msg: string; type: 'success' | 'error' } | null = null;

  availableColors = [
    '#8B3A3A', '#4A7C7E', '#9B8B6E', '#c87c7c',
    '#7ab5b7', '#b0a080', '#e67e22', '#3498db',
    '#27ae60', '#9b59b6', '#e74c3c', '#2c3e50'
  ];

  constructor(private http: HttpClient, private sanitizer: DomSanitizer) {}

  ngOnInit() { this.loadCategories(); }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => this.toast = null, 4000);
  }

  get filteredCategories(): Categorie[] {
    if (!this.searchTerm) return this.categories;
    const t = this.searchTerm.toLowerCase();
    return this.categories.filter(c =>
      c.nom.toLowerCase().includes(t) || (c.description || '').toLowerCase().includes(t)
    );
  }
  get visibleCount() { return this.categories.filter(c => c.visibleCatalogue).length; }
  get hiddenCount()  { return this.categories.filter(c => !c.visibleCatalogue).length; }

  loadCategories() {
    this.http.get<Categorie[]>(this.apiUrl, { headers: this.headers() })
      .subscribe({
        next: d => this.categories = d,
        error: () => this.showToast('Erreur de chargement', 'error')
      });
  }

  openCreateModal() {
    this.modalMode = 'create';
    this.form = this.emptyForm();
    this.imagePreview = null;
    this.selectedFile = null;
    this.showModal = true;
  }

  openEditModal(cat: Categorie) {
    this.modalMode = 'edit';
    this.form = { ...cat };
    this.imagePreview = cat.imageCouverture || null;
    this.selectedFile = null;
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
    this.imagePreview = null;
    this.selectedFile = null;
  }

  saveCategorie() {
    if (!this.form.nom?.trim()) { this.showToast('Le nom est obligatoire', 'error'); return; }
    if (!this.form.couleur)     { this.showToast('Choisissez une couleur', 'error'); return; }
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
    const url    = this.modalMode === 'create' ? this.apiUrl : `${this.apiUrl}/${this.form.id}`;
    const method = this.modalMode === 'create' ? 'post' : 'put';
    (this.http as any)[method](url, this.form, { headers: this.headers() }).subscribe({
      next: (res: any) => {
        const msg = this.modalMode === 'create'
          ? 'Catégorie créée avec succès ✓'
          : 'Catégorie mise à jour avec succès ✓';
        this.showToast(res.message || msg, 'success');
        this.isSaving = false;
        this.closeModal();
        this.loadCategories();
      },
      error: (err: any) => {
        this.showToast(err.error?.message || 'Erreur lors de la sauvegarde', 'error');
        this.isSaving = false;
      }
    });
  }

  deleteCategorie(id: number) {
    if (!confirm('Supprimer cette catégorie ?')) return;
    this.http.delete(`${this.apiUrl}/${id}`, { headers: this.headers() })
      .subscribe({
        next: (res: any) => { this.showToast(res.message || 'Catégorie supprimée'); this.loadCategories(); },
        error: (err: any) => this.showToast(err.error?.message || 'Erreur', 'error')
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

  // ── getCoverStyle — ngStyle ───────────────────────────────
  getCoverStyle(cat: Categorie): { [key: string]: string } {
    if (cat.imageCouverture) {
      return {
        'background-image': `url(${cat.imageCouverture})`,
        'background-size': 'cover',
        'background-position': 'center'
      };
    }
    return {
      'background': `linear-gradient(135deg, ${cat.couleur}30 0%, ${cat.couleur}60 100%)`
    };
  }

  private emptyForm(): Categorie {
    return {
      nom: '', description: '',
      couleur: '#8B3A3A',
      imageCouverture: '', metaDescription: '',
      ordreAffichage: 1, visibleCatalogue: true
    };
  }
}