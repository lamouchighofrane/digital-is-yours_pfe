import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-dashboard-formateur',
  templateUrl: './dashboard-formateur.component.html',
  styleUrls: ['./dashboard-formateur.component.css']
})
export class DashboardFormateurComponent implements OnInit, OnDestroy {

  activeSection: 'dashboard' | 'mes-formations' | 'profil' = 'dashboard';
  formateurUser: any = null;
  formations: any[] = [];
  stats: any = { totalApprenants: 0, tauxReussite: 0, nouveauxInscrits: 0, noteMoyenne: 0 };
  activites: any[] = [];
  alertes: any[] = [];

  // ── Notifications ──────────────────────────────────────────
  notifications: any[] = [];
  notifNonLues   = 0;
  showNotifPanel = false;
  notifLoading   = false;

  // ── Profil ─────────────────────────────────────────────────
  profilForm!: FormGroup;
  mdpForm!: FormGroup;
  profilActiveTab: 'identite' | 'securite' = 'identite';
  profilLoading  = false;
  profilSuccess  = '';
  profilError    = '';
  mdpLoading     = false;
  mdpSuccess     = '';
  mdpError       = '';
  showAncienMdp  = false;
  showNouveauMdp = false;
  showConfirmMdp = false;

  // ── photo : String — UserEntity (classe Utilisateur du diagramme) ──────
  // Affiché pour Formateur et Apprenant. Non utilisé dans le dashboard Admin.
  photoPreview: string | null = null;
  photoUploading = false;

  // ── competences : String — FormateurEntity (diagramme Formateur) ────────
  // Stocké en JSON ["Java","Angular",...] côté backend
  competences: string[] = [];
  competenceInput = '';

  // ── reseauxSociaux : JSON — FormateurEntity (diagramme Formateur) ───────
  // 1 seul objet JSON, PAS 4 champs séparés
  reseauxSociaux = { linkedin: '', twitter: '', portfolio: '', github: '' };

  // ── specialite, anneesExperience — hors diagramme, prévus par le client ─

  private api = 'http://localhost:8080/api/formateur';
  private pollingInterval: any = null;

  constructor(
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private fb: FormBuilder
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('formateur_token');
    if (!token) { this.router.navigate(['/login']); return; }
    this.formateurUser = JSON.parse(localStorage.getItem('formateur_user') || '{}');
    this.initForms();
    this.loadDashboardData();
    this.loadNotifications();
    this.pollingInterval = setInterval(() => this.pollNotifCount(), 30000);
  }

  ngOnDestroy() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('formateur_token')}` });
  }

  // ── Init forms ─────────────────────────────────────────────
  private initForms() {
    // reseauxSociaux (JSON) depuis le localStorage
    const rs = this.formateurUser?.reseauxSociaux || {};
    this.reseauxSociaux = {
      linkedin:  rs.linkedin  || '',
      twitter:   rs.twitter   || '',
      portfolio: rs.portfolio || '',
      github:    rs.github    || '',
    };

    // competences (List) depuis le localStorage
    this.competences = Array.isArray(this.formateurUser?.competences)
      ? [...this.formateurUser.competences]
      : [];

    // photo (String) depuis UserEntity
    this.photoPreview = this.formateurUser?.photo || null;

    // Formulaire (champs simples uniquement — reseauxSociaux et competences gérés à part)
    this.profilForm = this.fb.group({
      prenom:           [this.formateurUser?.prenom           || '', [Validators.required, Validators.minLength(2)]],
      nom:              [this.formateurUser?.nom              || '', [Validators.required, Validators.minLength(2)]],
      telephone:        [this.formateurUser?.telephone        || ''],
      specialite:       [this.formateurUser?.specialite       || ''],
      bio:              [this.formateurUser?.bio              || '', [Validators.maxLength(500)]],
      anneesExperience: [this.formateurUser?.anneesExperience || 0],
    });

    this.mdpForm = this.fb.group({
      ancienMotDePasse:  ['', [Validators.required]],
      nouveauMotDePasse: ['', [Validators.required, Validators.minLength(8)]],
      confirmMotDePasse: ['', [Validators.required]]
    });
  }

  setSection(section: 'dashboard' | 'mes-formations' | 'profil') {
    this.activeSection = section;
    if (section === 'mes-formations') this.loadFormations();
    if (section === 'profil') this.loadProfil();
    this.closeNotifPanel();
  }

  // ── Chargement ─────────────────────────────────────────────
  loadDashboardData() {
    this.loadFormations();
    this.http.get<any>(`${this.api}/stats`, { headers: this.headers() })
      .subscribe({ next: d => this.stats = d, error: () => {} });
    this.http.get<any[]>(`${this.api}/activites-recentes`, { headers: this.headers() })
      .subscribe({ next: d => this.activites = d || [], error: () => this.activites = [] });
    this.http.get<any[]>(`${this.api}/alertes`, { headers: this.headers() })
      .subscribe({ next: d => this.alertes = d || [], error: () => this.alertes = [] });
  }

  loadFormations() {
    this.http.get<any[]>(`${this.api}/mes-formations`, { headers: this.headers() })
      .subscribe({ next: d => this.formations = d || [], error: () => this.formations = [] });
  }

  // ── Charger profil depuis le backend ───────────────────────
  loadProfil() {
    this.http.get<any>(`${this.api}/profil`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.formateurUser = d;
          this.photoPreview = d.photo || null;
          this.competences = Array.isArray(d.competences) ? [...d.competences] : [];
          const rs = d.reseauxSociaux || {};
          this.reseauxSociaux = {
            linkedin:  rs.linkedin  || '',
            twitter:   rs.twitter   || '',
            portfolio: rs.portfolio || '',
            github:    rs.github    || '',
          };
          this.profilForm.patchValue({
            prenom:           d.prenom           || '',
            nom:              d.nom              || '',
            telephone:        d.telephone        || '',
            specialite:       d.specialite       || '',
            bio:              d.bio              || '',
            anneesExperience: d.anneesExperience || 0,
          });
          localStorage.setItem('formateur_user', JSON.stringify(d));
          this.cdr.detectChanges();
        },
        error: (err) => {
          // Ne PAS rediriger vers login — afficher le message d'erreur seulement
          // La redirection se fait uniquement si le token est vraiment expiré (401 au login)
          console.error('Erreur chargement profil:', err);
          this.profilError = 'Impossible de charger le profil. Veuillez réessayer.';
          this.cdr.detectChanges();
        }
      });
  }

  // ── Photo ──────────────────────────────────────────────────
  onPhotoSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { this.profilError = 'Veuillez sélectionner une image.'; return; }
    if (file.size > 2 * 1024 * 1024)    { this.profilError = 'La photo ne doit pas dépasser 2 Mo.'; return; }
    this.photoUploading = true;
    const reader = new FileReader();
    reader.onload = (e) => {
      this.photoPreview = e.target?.result as string;
      this.photoUploading = false;
      this.cdr.detectChanges();
    };
    reader.readAsDataURL(file);
  }

  removePhoto() { this.photoPreview = null; this.cdr.detectChanges(); }

  // ── Compétences ────────────────────────────────────────────
  addCompetence() {
    const t = this.competenceInput.trim();
    if (t && !this.competences.includes(t) && this.competences.length < 8) {
      this.competences.push(t);
      this.competenceInput = '';
      this.cdr.detectChanges();
    }
  }

  addCompetenceOnEnter(event: KeyboardEvent) {
    if (event.key === 'Enter') { event.preventDefault(); this.addCompetence(); }
  }

  removeCompetence(index: number) {
    this.competences.splice(index, 1);
    this.cdr.detectChanges();
  }

  get bioLength(): number {
    return this.profilForm.get('bio')?.value?.length || 0;
  }

  // ── Sauvegarder profil ─────────────────────────────────────
  saveProfil() {
    if (this.profilForm.invalid) { this.profilForm.markAllAsTouched(); return; }
    this.profilLoading = true; this.profilSuccess = ''; this.profilError = '';

    const payload = {
      // UserEntity
      ...this.profilForm.value,
      photo: this.photoPreview,               // photo : String (diagramme Utilisateur)
      // FormateurEntity
      competences:    this.competences,        // competences : String/JSON (diagramme Formateur)
      reseauxSociaux: this.reseauxSociaux,     // reseauxSociaux : JSON (diagramme Formateur)
    };

    this.http.put<any>(`${this.api}/profil`, payload, { headers: this.headers() })
      .subscribe({
        next: res => {
          this.profilLoading = false;
          this.profilSuccess = 'Profil mis à jour avec succès !';
          if (res.profil) {
            this.formateurUser = { ...this.formateurUser, ...res.profil };
            localStorage.setItem('formateur_user', JSON.stringify(this.formateurUser));
          }
          this.cdr.detectChanges();
          setTimeout(() => { this.profilSuccess = ''; this.cdr.detectChanges(); }, 3500);
        },
        error: err => {
          this.profilLoading = false;
          this.profilError = err.error?.message || 'Erreur lors de la mise à jour.';
          this.cdr.detectChanges();
        }
      });
  }

  // ── Changer mot de passe ───────────────────────────────────
  changerMotDePasse() {
    if (this.mdpForm.invalid) { this.mdpForm.markAllAsTouched(); return; }
    const { nouveauMotDePasse, confirmMotDePasse } = this.mdpForm.value;
    if (nouveauMotDePasse !== confirmMotDePasse) { this.mdpError = 'Les mots de passe ne correspondent pas.'; return; }
    this.mdpLoading = true; this.mdpSuccess = ''; this.mdpError = '';

    this.http.patch<any>(`${this.api}/profil/mot-de-passe`, this.mdpForm.value, { headers: this.headers() })
      .subscribe({
        next: () => {
          this.mdpLoading = false; this.mdpSuccess = 'Mot de passe modifié avec succès !';
          this.mdpForm.reset(); this.cdr.detectChanges();
          setTimeout(() => { this.mdpSuccess = ''; this.cdr.detectChanges(); }, 3500);
        },
        error: err => {
          this.mdpLoading = false;
          this.mdpError = err.error?.message || 'Erreur lors du changement.';
          this.cdr.detectChanges();
        }
      });
  }

  cancelProfil() {
    this.loadProfil(); this.profilSuccess = ''; this.profilError = '';
    this.setSection('dashboard');
  }

  isProfilInvalid(field: string): boolean {
    const c = this.profilForm.get(field); return !!(c && c.invalid && c.touched);
  }
  isMdpInvalid(field: string): boolean {
    const c = this.mdpForm.get(field); return !!(c && c.invalid && c.touched);
  }

  // ══ NOTIFICATIONS ══════════════════════════════════════════

  loadNotifications() {
    this.notifLoading = true;
    this.http.get<any[]>(`${this.api}/notifications`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.notifications = d || [];
          this.notifNonLues  = this.notifications.filter(n => !n.lu).length;
          this.notifLoading  = false;
          this.cdr.detectChanges();
        },
        error: () => { this.notifications = []; this.notifNonLues = 0; this.notifLoading = false; this.cdr.detectChanges(); }
      });
  }

  pollNotifCount() {
    this.http.get<any>(`${this.api}/notifications/count`, { headers: this.headers() })
      .subscribe({
        next: d => {
          const n = d.count || 0;
          if (n > this.notifNonLues) this.loadNotifications();
          else { this.notifNonLues = n; this.cdr.detectChanges(); }
        }, error: () => {}
      });
  }

  toggleNotifPanel(event: Event) {
    event.stopPropagation();
    this.showNotifPanel = !this.showNotifPanel;
    if (this.showNotifPanel && this.notifications.length === 0) this.loadNotifications();
    this.cdr.detectChanges();
  }

  closeNotifPanel() { this.showNotifPanel = false; this.cdr.detectChanges(); }

  marquerLue(notif: any) {
    if (notif.lu) return;
    this.http.patch(`${this.api}/notifications/${notif.id}/lire`, {}, { headers: this.headers() })
      .subscribe({ next: () => { notif.lu = true; this.notifNonLues = Math.max(0, this.notifNonLues - 1); this.cdr.detectChanges(); }, error: () => {} });
  }

  marquerToutesLues(event: Event) {
    event.stopPropagation();
    this.http.patch(`${this.api}/notifications/tout-lire`, {}, { headers: this.headers() })
      .subscribe({ next: () => { this.notifications.forEach(n => n.lu = true); this.notifNonLues = 0; this.showNotifPanel = false; this.cdr.detectChanges(); }, error: () => {} });
  }

  // ── Helpers ────────────────────────────────────────────────
  getNotifColor(t: string) { return t === 'FORMATION_AFFECTEE' ? '#4A7C7E' : t === 'FORMATION_RETIREE' ? '#8B3A3A' : '#9B8B6E'; }
  getNotifBg(t: string)    { return t === 'FORMATION_AFFECTEE' ? 'rgba(74,124,126,.12)' : t === 'FORMATION_RETIREE' ? 'rgba(139,58,58,.10)' : 'rgba(155,139,110,.10)'; }

  getTimeAgo(dateStr: string): string {
    if (!dateStr) return '';
    const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
    if (diff < 60)     return 'À l\'instant';
    if (diff < 3600)   return `Il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86400)  return `Il y a ${Math.floor(diff / 3600)}h`;
    if (diff < 604800) return `Il y a ${Math.floor(diff / 86400)}j`;
    return new Date(dateStr).toLocaleDateString('fr-FR');
  }

  getFormateurInitiales(): string {
    const u = this.formateurUser;
    return ((u?.prenom?.[0] || '') + (u?.nom?.[0] || '')).toUpperCase() || 'F';
  }

  getNiveauLabel(niveau: string): string {
    const m: any = { DEBUTANT: 'Débutant', INTERMEDIAIRE: 'Intermédiaire', AVANCE: 'Avancé' };
    return m[niveau] || niveau || '—';
  }

  getCoverStyle(f: any): { [key: string]: string } {
    if (f?.imageCouverture) return { 'background-image': `url(${f.imageCouverture})`, 'background-size': 'cover', 'background-position': 'center' };
    const c = f?.categorieCouleur || '#8B3A3A';
    return { 'background': `linear-gradient(135deg, ${c}30 0%, ${c}60 100%)` };
  }

  formatDate(d: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
  }

  logout() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
    localStorage.clear();
    this.router.navigate(['/login']);
  }
}