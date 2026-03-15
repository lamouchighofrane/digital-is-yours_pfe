import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-dashboard-apprenant',
  templateUrl: './dashboard-apprenant.component.html',
  styleUrls: ['./dashboard-apprenant.component.css']
})
export class DashboardApprenantComponent implements OnInit, OnDestroy {

  activeSection: 'dashboard' | 'formations' | 'profil' | 'progression' | 'certificats' = 'dashboard';
  apprenantUser: any = null;

  stats: any = {
    formationsEnCours: 0,
    formationsTerminees: 0,
    certificatsObtenus: 0,
    progressionGlobale: 0
  };

  formations: any[]      = [];
  formationsLoading      = false;
  formationsSearch       = '';
  formationsFilterNiveau = '';

  recommandations: any[]    = [];
  recommandationsLoading    = false;
  recommandationsError      = '';
  recommandationsRefreshing = false;
  profilIncomplet           = false; // ✅ Nouveau flag profil incomplet

  notifications: any[] = [];
  notifNonLues         = 0;
  showNotifPanel       = false;
  notifLoading         = false;

  profilActiveTab: 'identite' | 'preferences' | 'securite' = 'identite';
  profilLoading  = false;
  profilSuccess  = '';
  profilError    = '';
  mdpLoading     = false;
  mdpSuccess     = '';
  mdpError       = '';
  showAncienMdp  = false;
  showNouveauMdp = false;
  showConfirmMdp = false;

  photoPreview: string | null = null;
  photoUploading = false;

  profilForm = { prenom: '', nom: '', telephone: '', bio: '' };

  prefsForm = {
    niveauActuel: 'DEBUTANT',
    objectifsApprentissage: '',
    disponibilitesHeuresParSemaine: null as number | null
  };

  mdpForm = {
    ancienMotDePasse: '',
    nouveauMotDePasse: '',
    confirmMotDePasse: ''
  };

  domainesInput  = '';
  disponibilites: string[] = [];

  readonly joursDispos = [
    { key: 'LUN', label: 'Lun' }, { key: 'MAR', label: 'Mar' },
    { key: 'MER', label: 'Mer' }, { key: 'JEU', label: 'Jeu' },
    { key: 'VEN', label: 'Ven' }, { key: 'SAM', label: 'Sam' },
    { key: 'DIM', label: 'Dim' }
  ];

  readonly niveaux = [
    { value: 'DEBUTANT',      label: 'Débutant' },
    { value: 'INTERMEDIAIRE', label: 'Intermédiaire' },
    { value: 'AVANCE',        label: 'Avancé' }
  ];

  private api = 'http://localhost:8080/api/apprenant';
  private pollingInterval: any = null;

  constructor(
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('token');
    if (!token) { this.router.navigate(['/login']); return; }
    this.apprenantUser = JSON.parse(localStorage.getItem('user') || '{}');
    this.patchFormsFromUser(this.apprenantUser);
    this.loadDashboardData();
    this.loadNotifications();
    this.pollingInterval = setInterval(() => this.pollNotifCount(), 30000);
  }

  ngOnDestroy() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('token')}` });
  }

  setSection(s: 'dashboard' | 'formations' | 'profil' | 'progression' | 'certificats') {
    this.activeSection = s;
    if (s === 'formations') this.loadFormations();
    if (s === 'profil')     this.loadProfil();
    if (s === 'dashboard')  this.loadDashboardData();
    this.closeNotifPanel();
  }

  loadDashboardData() {
    this.loadFormations();
    this.loadRecommandations();
    this.http.get<any>(`${this.api}/stats`, { headers: this.headers() })
      .subscribe({ next: d => { this.stats = d; this.cdr.detectChanges(); }, error: () => {} });
  }

  // ✅ Recommandations avec gestion profil incomplet
  loadRecommandations() {
    this.recommandationsLoading = true;
    this.recommandationsError   = '';
    this.profilIncomplet        = false;

    this.http.get<any[]>(`${this.api}/recommandations`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.recommandations        = d || [];
          this.recommandationsLoading = false;
          // Si liste vide → profil incomplet (domaines et objectifs vides)
          if (this.recommandations.length === 0) {
            this.profilIncomplet = true;
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.recommandations        = [];
          this.recommandationsLoading = false;
          this.recommandationsError   = 'Impossible de charger les recommandations.';
          this.cdr.detectChanges();
        }
      });
  }

  rafraichirRecommandations() {
    this.recommandationsRefreshing = true;
    this.profilIncomplet           = false;
    this.http.delete(`${this.api}/recommandations/cache`, { headers: this.headers() })
      .subscribe({
        next: () => { this.recommandationsRefreshing = false; this.loadRecommandations(); },
        error: () => { this.recommandationsRefreshing = false; this.loadRecommandations(); }
      });
  }

  getScoreColor(score: number): string {
    if (score >= 80) return '#27ae60';
    if (score >= 60) return '#e67e22';
    if (score >= 40) return '#4A7C7E';
    return '#9B8B6E';
  }

  getScoreBg(score: number): string {
    if (score >= 80) return 'rgba(39,174,96,.1)';
    if (score >= 60) return 'rgba(230,126,34,.1)';
    if (score >= 40) return 'rgba(74,124,126,.1)';
    return 'rgba(155,139,110,.1)';
  }

  getScoreLabel(score: number): string {
    if (score >= 80) return 'Excellent';
    if (score >= 60) return 'Très bien';
    if (score >= 40) return 'Bien';
    return 'Possible';
  }

  voirFormation(formationId: number) {
    this.router.navigate(['/catalogue', formationId]);
  }

  loadFormations() {
    this.formationsLoading = true;
    this.http.get<any[]>(`${this.api}/mes-formations`, { headers: this.headers() })
      .subscribe({
        next: d => { this.formations = d || []; this.formationsLoading = false; this.cdr.detectChanges(); },
        error: () => { this.formations = []; this.formationsLoading = false; this.cdr.detectChanges(); }
      });
  }

  get filteredFormations(): any[] {
    return this.formations.filter(f => {
      const matchSearch = !this.formationsSearch ||
        f.titre?.toLowerCase().includes(this.formationsSearch.toLowerCase());
      const matchNiveau = !this.formationsFilterNiveau || f.niveau === this.formationsFilterNiveau;
      return matchSearch && matchNiveau;
    });
  }

  loadProfil() {
    this.http.get<any>(`${this.api}/profil`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.apprenantUser = d;
          this.patchFormsFromUser(d);
          localStorage.setItem('user', JSON.stringify(d));
          this.cdr.detectChanges();
        },
        error: () => { this.profilError = 'Impossible de charger le profil.'; this.cdr.detectChanges(); }
      });
  }

  patchFormsFromUser(u: any) {
    this.profilForm = {
      prenom:    u?.prenom    || '',
      nom:       u?.nom       || '',
      telephone: u?.telephone || '',
      bio:       u?.bio       || ''
    };
    this.photoPreview = u?.photo || null;
    this.prefsForm = {
      niveauActuel:                   u?.niveauActuel || 'DEBUTANT',
      objectifsApprentissage:         u?.objectifsApprentissage || '',
      disponibilitesHeuresParSemaine: u?.disponibilitesHeuresParSemaine ?? null
    };
    this.domainesInput = Array.isArray(u?.domainesInteret)
      ? u.domainesInteret.join(', ')
      : (u?.domainesInteret || '');
    this.disponibilites = Array.isArray(u?.disponibilites) ? [...u.disponibilites] : [];
  }

  saveProfil() {
    this.profilLoading = true; this.profilSuccess = ''; this.profilError = '';
    const payload = { ...this.profilForm, photo: this.photoPreview };
    this.http.put<any>(`${this.api}/profil`, payload, { headers: this.headers() })
      .subscribe({
        next: res => {
          this.profilLoading = false;
          this.profilSuccess = 'Profil mis à jour avec succès !';
          if (res.profil) {
            this.apprenantUser = { ...this.apprenantUser, ...res.profil };
            localStorage.setItem('user', JSON.stringify(this.apprenantUser));
          }
          this.cdr.detectChanges();
          setTimeout(() => { this.profilSuccess = ''; this.cdr.detectChanges(); }, 3500);
        },
        error: err => {
          this.profilLoading = false;
          this.profilError   = err.error?.message || 'Erreur lors de la mise à jour.';
          this.cdr.detectChanges();
        }
      });
  }

  // ✅ Après sauvegarde préférences → recalcul recommandations automatique
  savePreferences() {
    this.profilLoading = true; this.profilSuccess = ''; this.profilError = '';
    const payload = {
      ...this.prefsForm,
      domainesInteret: this.domainesInput
        .split(',').map(d => d.trim()).filter(d => d.length > 0),
      disponibilites: this.disponibilites
    };
    this.http.put<any>(`${this.api}/profil`, payload, { headers: this.headers() })
      .subscribe({
        next: res => {
          this.profilLoading = false;
          this.profilSuccess = 'Préférences enregistrées !';
          if (res.profil) {
            this.apprenantUser = { ...this.apprenantUser, ...res.profil };
            localStorage.setItem('user', JSON.stringify(this.apprenantUser));
          }
          // ✅ Invalider le cache et recalculer les recommandations
          this.profilIncomplet = false;
          this.http.delete(`${this.api}/recommandations/cache`, { headers: this.headers() })
            .subscribe({ next: () => this.loadRecommandations(), error: () => this.loadRecommandations() });
          this.cdr.detectChanges();
          setTimeout(() => { this.profilSuccess = ''; this.cdr.detectChanges(); }, 3500);
        },
        error: err => {
          this.profilLoading = false;
          this.profilError   = err.error?.message || 'Erreur.';
          this.cdr.detectChanges();
        }
      });
  }

  changerMotDePasse() {
    this.mdpSuccess = ''; this.mdpError = '';
    if (!this.mdpForm.ancienMotDePasse || !this.mdpForm.nouveauMotDePasse || !this.mdpForm.confirmMotDePasse) {
      this.mdpError = 'Tous les champs sont requis.'; return;
    }
    if (this.mdpForm.nouveauMotDePasse !== this.mdpForm.confirmMotDePasse) {
      this.mdpError = 'Les mots de passe ne correspondent pas.'; return;
    }
    if (this.mdpForm.nouveauMotDePasse.length < 8) {
      this.mdpError = 'Minimum 8 caractères requis.'; return;
    }
    this.mdpLoading = true;
    this.http.patch<any>(`${this.api}/profil/mot-de-passe`, this.mdpForm, { headers: this.headers() })
      .subscribe({
        next: () => {
          this.mdpLoading = false;
          this.mdpSuccess = 'Mot de passe modifié avec succès !';
          this.resetMdpForm();
          this.cdr.detectChanges();
          setTimeout(() => { this.mdpSuccess = ''; this.cdr.detectChanges(); }, 3500);
        },
        error: err => {
          this.mdpLoading = false;
          this.mdpError   = err.error?.message || 'Erreur lors du changement.';
          this.cdr.detectChanges();
        }
      });
  }

  resetMdpForm() {
    this.mdpForm    = { ancienMotDePasse: '', nouveauMotDePasse: '', confirmMotDePasse: '' };
    this.mdpError   = '';
    this.mdpSuccess = '';
  }

  onPhotoSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { this.profilError = 'Veuillez sélectionner une image.'; return; }
    if (file.size > 2 * 1024 * 1024)    { this.profilError = 'La photo ne doit pas dépasser 2 Mo.'; return; }
    this.photoUploading = true;
    const reader = new FileReader();
    reader.onload = (e) => {
      this.photoPreview   = e.target?.result as string;
      this.photoUploading = false;
      this.cdr.detectChanges();
    };
    reader.readAsDataURL(file);
  }

  removePhoto() { this.photoPreview = null; this.cdr.detectChanges(); }

  toggleJour(j: string) {
    const idx = this.disponibilites.indexOf(j);
    if (idx >= 0) this.disponibilites.splice(idx, 1);
    else this.disponibilites.push(j);
    this.cdr.detectChanges();
  }

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
        error: () => {
          this.notifications = []; this.notifNonLues = 0;
          this.notifLoading  = false; this.cdr.detectChanges();
        }
      });
  }

  pollNotifCount() {
    this.http.get<any>(`${this.api}/notifications/count`, { headers: this.headers() })
      .subscribe({
        next: d => {
          const n = d.count || 0;
          if (n > this.notifNonLues) this.loadNotifications();
          else { this.notifNonLues = n; this.cdr.detectChanges(); }
        },
        error: () => {}
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
      .subscribe({
        next: () => {
          notif.lu          = true;
          this.notifNonLues = Math.max(0, this.notifNonLues - 1);
          this.cdr.detectChanges();
        },
        error: () => {}
      });
  }

  marquerToutesLues(event: Event) {
    event.stopPropagation();
    this.http.patch(`${this.api}/notifications/tout-lire`, {}, { headers: this.headers() })
      .subscribe({
        next: () => {
          this.notifications.forEach(n => n.lu = true);
          this.notifNonLues   = 0;
          this.showNotifPanel = false;
          this.cdr.detectChanges();
        },
        error: () => {}
      });
  }

  getPwStrength(): number {
    const p = this.mdpForm.nouveauMotDePasse;
    if (!p) return 0;
    let s = 0;
    if (p.length >= 8)          s++;
    if (/[A-Z]/.test(p))        s++;
    if (/[0-9]/.test(p))        s++;
    if (/[^A-Za-z0-9]/.test(p)) s++;
    return s;
  }

  getPwStrengthLabel(): string {
    return ['', 'Faible', 'Moyen', 'Fort', 'Très fort'][this.getPwStrength()] || '';
  }

  hasMaj(): boolean     { return /[A-Z]/.test(this.mdpForm.nouveauMotDePasse); }
  hasChiffre(): boolean { return /[0-9]/.test(this.mdpForm.nouveauMotDePasse); }
  hasSpecial(): boolean { return /[^A-Za-z0-9]/.test(this.mdpForm.nouveauMotDePasse); }

  mdpMatch(): boolean {
    return !!this.mdpForm.confirmMotDePasse &&
           this.mdpForm.confirmMotDePasse === this.mdpForm.nouveauMotDePasse;
  }
  mdpNoMatch(): boolean {
    return !!this.mdpForm.confirmMotDePasse &&
           this.mdpForm.confirmMotDePasse !== this.mdpForm.nouveauMotDePasse;
  }

  getInitiales(): string {
    const p = this.profilForm.prenom?.[0] || '';
    const n = this.profilForm.nom?.[0]    || '';
    return (p + n).toUpperCase() || '?';
  }

  getNiveauLabel(niveau: string): string {
    const m: any = { DEBUTANT: 'Débutant', INTERMEDIAIRE: 'Intermédiaire', AVANCE: 'Avancé' };
    return m[niveau] || niveau || '—';
  }

  formatDate(d: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
  }

  getProgressionColor(p: number): string {
    if (p >= 80) return '#27ae60';
    if (p >= 50) return '#e67e22';
    return '#4A7C7E';
  }

  getNotifColor(t: string) {
    return t === 'NOUVELLE_FORMATION' ? '#4A7C7E'
         : t === 'CERTIFICAT'         ? '#27ae60'
         : '#9B8B6E';
  }

  getNotifBg(t: string) {
    return t === 'NOUVELLE_FORMATION' ? 'rgba(74,124,126,.12)'
         : t === 'CERTIFICAT'         ? 'rgba(39,174,96,.1)'
         : 'rgba(155,139,110,.1)';
  }

  getTimeAgo(dateStr: string): string {
    if (!dateStr) return '';
    const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
    if (diff < 60)     return 'À l\'instant';
    if (diff < 3600)   return `Il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86400)  return `Il y a ${Math.floor(diff / 3600)}h`;
    if (diff < 604800) return `Il y a ${Math.floor(diff / 86400)}j`;
    return new Date(dateStr).toLocaleDateString('fr-FR');
  }

  getCoverStyle(f: any): { [key: string]: string } {
    if (f?.imageCouverture) return {
      'background-image':    `url(${f.imageCouverture})`,
      'background-size':     'cover',
      'background-position': 'center'
    };
    return { 'background': 'linear-gradient(135deg, #4A7C7E30 0%, #4A7C7E60 100%)' };
  }

  logout() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
    localStorage.clear();
    this.router.navigate(['/login']);
  }
}