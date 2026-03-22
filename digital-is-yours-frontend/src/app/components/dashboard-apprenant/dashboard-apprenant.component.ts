import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-dashboard-apprenant',
  templateUrl: './dashboard-apprenant.component.html',
  styleUrls: ['./dashboard-apprenant.component.css']
})
export class DashboardApprenantComponent implements OnInit, OnDestroy {

  activeSection: 'dashboard' | 'formations' | 'profil' | 'progression' | 'certificats' | 'calendrier' | 'cours' = 'dashboard';
  // ── Ma Progression ──
progressionFormations: any[]  = [];
progressionLoading            = false;
progressionError              = '';
progressionStatsGlobales = {
  totalFormations: 0,
  formationsTerminees: 0,
  formationsEnCours: 0,
  scoreMoyen: 0
};
  // ── Variables QR Code ──
  linkedInQrUrl: string | null = null;
  linkedInQrLoading             = false;
  linkedInAnimating             = false;  // ← animation envol
  // ══════════════════════════════════════════════════════
// CALENDRIER
// ══════════════════════════════════════════════════════
sessions: any[]        = [];
sessionsLoading        = false;
calViewMode: 'mois' | 'semaine' | 'jour' = 'mois';
calCurrentDate         = new Date();
showSessionModal       = false;
sessionModalMode: 'ajouter' | 'modifier' = 'ajouter';
sessionEnEdition: any  = null;
sessionForm = {
  titre:        '',
  formationId:  null as number | null,
  dateSession:  '',
  heure:        '09:00',
  dureeMinutes: 60,
  typeSession:  'COURS',
  notes:        '',
  rappel24h:    true
};
sessionLoading  = false;
sessionSuccess  = '';
sessionError    = '';
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
  // ── Section Cours (US-028) ──
selectedFormation: any = null;
cours: any[]           = [];
coursLoading           = false;
coursError             = '';
coursActiveTab: 'cours' | 'progression' | 'forum' | 'ressources' = 'cours';
quizNotePassage: number | null = null;
quizExiste: boolean = false;
// ── US-033 : Quiz Final ──
showQuizFinalModal   = false;
qfEtape: 'chargement' | 'intro' | 'question' | 'resultat' = 'chargement';
qfData: any          = null;
qfQuestionIndex      = 0;
qfReponses: { [questionId: number]: number } = {};
qfResultat: any      = null;
qfSoumission         = false;
qfTempsTotal         = 0;
qfTempsRestant       = 0;
qfConfetti: any[]    = [];
qfConditionsAcceptees = false;
showQfConfirm        = false;
private qfTimerInterval: any = null;
// ── US-048 : Certificats ──           // ← ajouter ici
certificats: any[]     = [];
certificatsLoading     = false;
qfCertificat: any      = null;
qfCertDownloading      = false;
// ── US-029 : Cours actif + documents + vidéo ──
coursActif: any = null;
showCoursDetail = false;
documentsActifs: any[] = [];
documentsLoading = false;
documentsError = '';

  recommandations: any[]    = [];
  recommandationsLoading    = false;
  recommandationsError      = '';
  recommandationsRefreshing = false;
  profilIncomplet           = false;

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

  // ══════════════════════════════════════════════════════
  // PAIEMENT
  // ══════════════════════════════════════════════════════

  showPaiementModal   = false;
  paiementFormation: any = null;   // formation courante (depuis recommandations)
  paiementEtape: 'formulaire' | 'traitement' | 'succes' | 'echec' = 'formulaire';
  paiementReference   = '';
  paiementMontant     = 0;
  paiementErreur      = '';
  showCvv             = false;

  carteForm = {
    numeroCarte: '',
    nomCarte:    '',
    expiration:  '',
    cvv:         ''
  };
  

  // ──────────────────────────────────────────────────────

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

  private api              = 'http://localhost:8080/api/apprenant';
  private pollingInterval: any = null;

 constructor(
  private router: Router,
  private route: ActivatedRoute,   // ← ajouter
  private http: HttpClient,
  private cdr: ChangeDetectorRef,
  private sanitizer: DomSanitizer 
  
  
) {}

  // ══════════════════════════════════════════════════════
  // LIFECYCLE
  // ══════════════════════════════════════════════════════

  ngOnInit() {
  const token = localStorage.getItem('token');
  if (!token) { this.router.navigate(['/login']); return; }
  this.apprenantUser = JSON.parse(localStorage.getItem('user') || '{}');
  this.patchFormsFromUser(this.apprenantUser);
  this.loadDashboardData();
  this.loadNotifications();
  this.pollingInterval = setInterval(() => this.pollNotifCount(), 30000);
  
  const tab = this.route.snapshot.queryParamMap.get('tab');
  if (tab === 'mes-formations') {
    this.setSection('formations');
  }
  
  const section = this.route.snapshot.queryParamMap.get('section');
  if (section === 'calendrier') {
    this.setSection('calendrier');
  }
  
  // ← NOUVEAU BLOC :
  const formationId = this.route.snapshot.queryParamMap.get('formationId');
  if (formationId) {
    const fid = +formationId;
    this.http.get<any[]>(`${this.api}/formations/mes-inscriptions`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.formations = d || [];
          this.formationsLoading = false;
          const formation = this.formations.find(
            f => (f.formationId || f.id) === fid
          );
          if (formation) {
            this.selectedFormation = formation;
            this.activeSection = 'cours' as any;
            this.coursActiveTab = 'cours';
            this.cours = [];
            this.coursError = '';
            this.loadCours();
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.formationsLoading = false;
          this.cdr.detectChanges();
        }
      });
  }
}

  ngOnDestroy() {
  if (this.pollingInterval) clearInterval(this.pollingInterval);
  this.stopTimerQf();
}

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('token')}` });
  }

  // ══════════════════════════════════════════════════════
  // NAVIGATION
  // ══════════════════════════════════════════════════════

setSection(s: 'dashboard' | 'formations' | 'profil' | 'progression' | 'certificats' | 'calendrier') {
  this.activeSection = s;
  if (s === 'formations')  this.loadFormations();
  if (s === 'profil')      this.loadProfil();
  if (s === 'dashboard')   this.loadDashboardData();
  if (s === 'calendrier')  this.loadSessions();
  if (s === 'progression') this.loadProgressionData();
  this.closeNotifPanel();
  if (s === 'certificats') this.loadCertificats();
}
voirCours(formation: any) {
  this.selectedFormation = formation;
  this.activeSection     = 'cours' as any;
  this.coursActiveTab    = 'cours';
  this.cours             = [];
  this.coursError        = '';
  
  console.log('formation sélectionnée:', formation); // ← DEBUG temporaire
  console.log('formationId:', formation.formationId, 'id:', formation.id);
  
  this.loadCours();
  this.closeNotifPanel();
}

retourFormations() {
  this.selectedFormation = null;
  this.cours = [];
  this.activeSection = 'formations';
  this.cdr.detectChanges();
}

  goToCatalogue() {
    this.router.navigate(['/']);
  }

  // ══════════════════════════════════════════════════════
  // DASHBOARD
  // ══════════════════════════════════════════════════════

  loadDashboardData() {
    this.loadFormations();
    this.loadRecommandations();
    this.http.get<any>(`${this.api}/stats`, { headers: this.headers() })
      .subscribe({ next: d => { this.stats = d; this.cdr.detectChanges(); }, error: () => {} });
  }

  // ══════════════════════════════════════════════════════
  // RECOMMANDATIONS IA
  // ══════════════════════════════════════════════════════

  loadRecommandations() {
    this.recommandationsLoading = true;
    this.recommandationsError   = '';
    this.profilIncomplet        = false;

    this.http.get<any[]>(`${this.api}/recommandations`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.recommandations        = d || [];
          this.recommandationsLoading = false;
          if (this.recommandations.length === 0) this.profilIncomplet = true;
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
        next:  () => { this.recommandationsRefreshing = false; this.loadRecommandations(); },
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

  /** Clique sur "Voir la formation" → ouvre le modal paiement */
  voirFormation(formationId: number) {
    // Chercher la formation dans les recommandations
    const reco = this.recommandations.find(r => r.formationId === formationId || r.id === formationId);
    this.ouvrirModalPaiement(reco || { formationId, id: formationId });
  }

  // ══════════════════════════════════════════════════════
  // PAIEMENT — MODAL
  // ══════════════════════════════════════════════════════

  /** Ouvre le modal paiement pour une formation donnée */
  ouvrirModalPaiement(formation: any) {
    const formationId = formation.formationId || formation.id;

    // 1. Vérifier si déjà inscrit
    this.http.get<any>(
      `http://localhost:8080/api/apprenant/formations/${formationId}/statut-inscription`,
      { headers: this.headers() }
    ).subscribe({
      next: res => {
        if (res.inscrit) {
          // Déjà inscrit → aller directement à la section formations
          this.setSection('formations');
          return;
        }
        // 2. Initier le paiement (crée l'inscription EN_ATTENTE)
        this.http.post<any>(
          `http://localhost:8080/api/apprenant/formations/${formationId}/paiement/initier`,
          {},
          { headers: this.headers() }
        ).subscribe({
          next: info => {
            this.paiementFormation = { ...formation, prix: info.montant, titre: info.formationTitre };
            this.paiementMontant   = info.montant;
            this.paiementEtape     = 'formulaire';
            this.paiementErreur    = '';
            this.carteForm         = { numeroCarte: '', nomCarte: '', expiration: '', cvv: '' };
            this.showPaiementModal = true;
            this.cdr.detectChanges();
          },
          error: err => {
            const msg = err.error?.message || 'Impossible d\'initier le paiement';
            if (msg.includes('déjà inscrit')) {
              this.setSection('formations');
            } else {
              alert(msg);
            }
          }
        });
      },
      error: () => alert('Erreur de vérification. Veuillez réessayer.')
    });
  }

  /** Ferme le modal */
  fermerModalPaiement() {
    this.showPaiementModal = false;
    this.paiementFormation = null;
    this.paiementEtape     = 'formulaire';
    this.paiementErreur    = '';
    this.cdr.detectChanges();
  }

  /** Soumet le formulaire de paiement */
  payerFormation() {
    if (!this.paiementFormation) return;
    const formationId = this.paiementFormation.formationId || this.paiementFormation.id;

    this.paiementEtape  = 'traitement';
    this.paiementErreur = '';
    this.cdr.detectChanges();

    // Simuler un délai réseau pour l'effet visuel
    setTimeout(() => {
      this.http.post<any>(
        `http://localhost:8080/api/apprenant/formations/${formationId}/paiement/confirmer`,
        {
          numeroCarte: this.carteForm.numeroCarte,
          nomCarte:    this.carteForm.nomCarte,
          expiration:  this.carteForm.expiration,
          cvv:         this.carteForm.cvv
        },
        { headers: this.headers() }
      ).subscribe({
        next: res => {
          this.paiementReference = res.reference;
          this.paiementEtape     = 'succes';
          // Recharger les formations
          this.loadFormations();
          this.loadDashboardData();
          this.cdr.detectChanges();
        },
        error: err => {
          this.paiementErreur = err.error?.message || 'Paiement refusé. Veuillez réessayer.';
          this.paiementEtape  = 'echec';
          this.cdr.detectChanges();
        }
      });
    }, 2000); // 2s délai pour l'animation
  }

  /** Réessayer après un échec */
  reessayerPaiement() {
    this.paiementEtape  = 'formulaire';
    this.paiementErreur = '';
    this.carteForm      = { numeroCarte: '', nomCarte: '', expiration: '', cvv: '' };
    this.cdr.detectChanges();
  }

  /** Formatage automatique du numéro de carte (XXXX XXXX XXXX XXXX) */
  formatCarteNumero(event: Event) {
    const input = event.target as HTMLInputElement;
    let val = input.value.replace(/\D/g, '').substring(0, 16);
    val = val.replace(/(.{4})/g, '$1 ').trim();
    this.carteForm.numeroCarte = val;
    input.value = val;
  }

  /** Formatage automatique expiration (MM/AA) */
  formatExpiration(event: Event) {
    const input = event.target as HTMLInputElement;
    let val = input.value.replace(/\D/g, '').substring(0, 4);
    if (val.length >= 2) val = val.substring(0, 2) + '/' + val.substring(2);
    this.carteForm.expiration = val;
    input.value = val;
  }

  /** Icône selon type de carte (Visa / Mastercard) */
  getCarteType(): 'visa' | 'mastercard' | 'unknown' {
    const n = this.carteForm.numeroCarte.replace(/\s/g, '');
    if (n.startsWith('4')) return 'visa';
    if (n.startsWith('5') || n.startsWith('2')) return 'mastercard';
    return 'unknown';
  }

  /** Formulaire valide ? */
  get paiementFormValide(): boolean {
    const n = this.carteForm.numeroCarte.replace(/\s/g, '');
    return n.length === 16 &&
           this.carteForm.nomCarte.trim().length > 0 &&
           /^\d{2}\/\d{2}$/.test(this.carteForm.expiration) &&
           this.carteForm.cvv.length >= 3;
  }

  // ══════════════════════════════════════════════════════
  // MES FORMATIONS
  // ══════════════════════════════════════════════════════

  loadFormations() {
    this.formationsLoading = true;
    this.http.get<any[]>(`${this.api}/formations/mes-inscriptions`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.formations        = d || [];
          this.formationsLoading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.formations        = [];
          this.formationsLoading = false;
          this.cdr.detectChanges();
        }
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
 loadCours() {
  if (!this.selectedFormation) return;
  
  // formationId d'abord (ID de la formation), sinon id (ID inscription)
  const fid = this.selectedFormation.formationId || this.selectedFormation.id;
  
  this.coursLoading = true;
  this.coursError   = '';
  this.http.get<any>(`${this.api}/formations/${fid}/cours`, { headers: this.headers() })
    .subscribe({
      next: res => { 
        this.cours = res.cours || [];
        // ← Récupérer les infos du quiz final
        if (res.quiz) {
         this.quizNotePassage = res.quiz.notePassage ?? null;
          this.quizExiste      = res.quiz.existe || false;
        }
        this.coursLoading = false; 
        this.cdr.detectChanges(); 
      },
      error: err => { 
        this.coursError = err.error?.message || 'Impossible de charger les cours.'; 
        this.coursLoading = false; 
        this.cdr.detectChanges(); 
      }
    });
}

getCoursStatut(c: any, i: number): 'termine' | 'en-cours' | 'disponible' | 'verrouille' {
  if (c.estTermine) return 'termine';
  if (c.estEnCours) return 'en-cours';
  if (i > 0 && !this.cours[i - 1].estTermine) return 'verrouille';
  return 'disponible';
}

get coursTerminesCount(): number { return this.cours.filter(c => c.estTermine).length; }

get progressionPct(): number {
  if (this.cours.length) return Math.round((this.coursTerminesCount / this.cours.length) * 100);
  return this.selectedFormation?.progression || 0;
}

continuerFormation() {
  const premier = this.cours.find((c, i) => this.getCoursStatut(c, i) !== 'termine');
  if (premier) console.log('Continuer:', premier.titre);
}
// ── Suivre un cours ─────────────────────────────────────

ouvrirCours(c: any, i: number) {
  const statut = this.getCoursStatut(c, i);
  if (statut === 'verrouille') return;
  
  const fid = this.selectedFormation?.formationId || this.selectedFormation?.id;
  
  this.router.navigate(
    ['/apprenant/cours', fid, c.id],
    { queryParams: { titre: this.selectedFormation?.titre } }
  );
}


fermerCours() {
  this.coursActif      = null;
  this.showCoursDetail = false;
  this.documentsActifs = [];
}

loadDocumentsCours(c: any) {
  if (!this.selectedFormation) return;
  const fid = this.selectedFormation.formationId || this.selectedFormation.id;
  this.documentsLoading = true;
  this.http.get<any>(
    `${this.api}/formations/${fid}/cours/${c.id}/documents`,
    { headers: this.headers() }
  ).subscribe({
    next: res => {
      this.documentsActifs  = res.documents || [];
      this.documentsLoading = false;
      this.cdr.detectChanges();
    },
    error: () => {
      this.documentsActifs  = [];
      this.documentsLoading = false;
      this.cdr.detectChanges();
    }
  });
}

getVideoUrl(c: any): SafeResourceUrl {
  if (!c || !c.videoType) return '';
  const fid = this.selectedFormation?.formationId || this.selectedFormation?.id;
  if (c.videoType === 'YOUTUBE') {
    return this.sanitizer.bypassSecurityTrustResourceUrl(c.videoUrl || '');
  }
  if (c.videoType === 'LOCAL' && c.videoUrl) {
    const url = `http://localhost:8080/api/apprenant/cours/${c.id}/video/stream/${c.videoUrl}?formationId=${fid}`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }
  return '';
}

getYoutubeEmbedUrl(url: string): SafeResourceUrl {
  if (!url) return '';
  const m = url.match(
    /(?:youtube\.com\/(?:watch\?v=|shorts\/|embed\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/
  );
  const embedUrl = m ? `https://www.youtube.com/embed/${m[1]}?autoplay=0&rel=0` : '';
  return this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl);
}

downloadDocument(doc: any) {
  if (!this.selectedFormation || !this.coursActif) return;
  const fid = this.selectedFormation.formationId || this.selectedFormation.id;
  const token = localStorage.getItem('token');
  fetch(
    `http://localhost:8080/api/apprenant/formations/${fid}/cours/${this.coursActif.id}/documents/${doc.id}/download`,
    { headers: { Authorization: `Bearer ${token}` } }
  ).then(res => res.blob()).then(blob => {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = doc.nomFichier || doc.titre;
    a.click();
    window.URL.revokeObjectURL(url);
  }).catch(() => {});
}

getDocIconLabel(type: string): string {
  if (!type) return 'FILE';
  if (type.includes('pdf')) return 'PDF';
  if (type.includes('word') || type.includes('msword')) return 'DOC';
  if (type.includes('powerpoint') || type.includes('presentation')) return 'PPT';
  if (type.includes('excel') || type.includes('sheet')) return 'XLS';
  if (type.includes('image')) return 'IMG';
  if (type.includes('text')) return 'TXT';
  return 'FILE';
}

formatTailleDoc(bytes: number): string {
  if (!bytes) return '';
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} Ko`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
}



  // ══════════════════════════════════════════════════════
  // PROFIL
  // ══════════════════════════════════════════════════════

  loadProfil() {
    this.http.get<any>(`${this.api}/profil`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.apprenantUser = d;
          this.patchFormsFromUser(d);
          localStorage.setItem('user', JSON.stringify(d));
          this.cdr.detectChanges();
        },
        error: () => {
          this.profilError = 'Impossible de charger le profil.';
          this.cdr.detectChanges();
        }
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
    this.profilLoading = true;
    this.profilSuccess = '';
    this.profilError   = '';
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

  savePreferences() {
    this.profilLoading = true;
    this.profilSuccess = '';
    this.profilError   = '';
    const payload = {
      ...this.prefsForm,
      domainesInteret: this.domainesInput
        .split(',').map((d: string) => d.trim()).filter((d: string) => d.length > 0),
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
          this.profilIncomplet = false;
          this.http.delete(`${this.api}/recommandations/cache`, { headers: this.headers() })
            .subscribe({
              next: () => this.loadRecommandations(),
              error: () => this.loadRecommandations()
            });
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
    this.mdpSuccess = '';
    this.mdpError   = '';
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
    else          this.disponibilites.push(j);
    this.cdr.detectChanges();
  }

  // ══════════════════════════════════════════════════════
  // NOTIFICATIONS
  // ══════════════════════════════════════════════════════

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
          this.notifications = [];
          this.notifNonLues  = 0;
          this.notifLoading  = false;
          this.cdr.detectChanges();
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

  // ══════════════════════════════════════════════════════
  // HELPERS — MOT DE PASSE
  // ══════════════════════════════════════════════════════

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

  // ══════════════════════════════════════════════════════
  // HELPERS — AFFICHAGE
  // ══════════════════════════════════════════════════════

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
  if (p >= 100) return '#27ae60';
  if (p >= 50)  return '#4A7C7E';
  if (p > 0)    return '#f39c12';
  return '#C8BEB2';
}

  getNotifColor(t: string): string {
    return t === 'NOUVELLE_FORMATION' ? '#4A7C7E'
         : t === 'CERTIFICAT'         ? '#27ae60'
         : '#9B8B6E';
  }

  getNotifBg(t: string): string {
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

  // ══════════════════════════════════════════════════════
  // LOGOUT
  // ══════════════════════════════════════════════════════

  logout() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
    localStorage.clear();
    this.router.navigate(['/login']);
  }
  // ══════════════════════════════════════════════════════
// CALENDRIER — MÉTHODES
// ══════════════════════════════════════════════════════

loadSessions() {
  this.sessionsLoading = true;
  this.http.get<any[]>(`${this.api}/calendrier`, { headers: this.headers() })
    .subscribe({
      next: d => {
        this.sessions = (d || []).map(s => ({
          ...s,
          dateSession: new Date(s.dateSession)
        }));
        this.sessionsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.sessions = [];
        this.sessionsLoading = false;
        this.cdr.detectChanges();
      }
    });
}

// Navigation calendrier
calPrecedent() {
  const d = new Date(this.calCurrentDate);
  if (this.calViewMode === 'mois')   d.setMonth(d.getMonth() - 1);
  if (this.calViewMode === 'semaine') d.setDate(d.getDate() - 7);
  if (this.calViewMode === 'jour')   d.setDate(d.getDate() - 1);
  this.calCurrentDate = d;
  this.cdr.detectChanges();
}

calSuivant() {
  const d = new Date(this.calCurrentDate);
  if (this.calViewMode === 'mois')   d.setMonth(d.getMonth() + 1);
  if (this.calViewMode === 'semaine') d.setDate(d.getDate() + 7);
  if (this.calViewMode === 'jour')   d.setDate(d.getDate() + 1);
  this.calCurrentDate = d;
  this.cdr.detectChanges();
}

calAujourdhui() {
  this.calCurrentDate = new Date();
  this.cdr.detectChanges();
}

// Titre du mois/semaine affiché
get calTitre(): string {
  const opts: Intl.DateTimeFormatOptions = { month: 'long', year: 'numeric' };
  return this.calCurrentDate.toLocaleDateString('fr-FR', opts)
    .replace(/^\w/, c => c.toUpperCase());
}

// Génération grille mois (6 semaines x 7 jours)
get calJoursMois(): Date[] {
  const debut = new Date(
    this.calCurrentDate.getFullYear(),
    this.calCurrentDate.getMonth(), 1);
  const fin = new Date(
    this.calCurrentDate.getFullYear(),
    this.calCurrentDate.getMonth() + 1, 0);

  // Lundi = 0
  let jourDebut = debut.getDay() === 0 ? 6 : debut.getDay() - 1;
  const grid: Date[] = [];

  for (let i = jourDebut; i > 0; i--) {
    const d = new Date(debut);
    d.setDate(d.getDate() - i);
    grid.push(d);
  }
  for (let d = new Date(debut); d <= fin; d.setDate(d.getDate() + 1)) {
    grid.push(new Date(d));
  }
  while (grid.length < 42) {
    const last = grid[grid.length - 1];
    const next = new Date(last);
    next.setDate(next.getDate() + 1);
    grid.push(next);
  }
  return grid;
}

// Sessions d'un jour donné
getSessionsDuJour(date: Date): any[] {
  return this.sessions.filter(s => {
    const sd = new Date(s.dateSession);
    return sd.getDate()     === date.getDate() &&
           sd.getMonth()    === date.getMonth() &&
           sd.getFullYear() === date.getFullYear();
  }).sort((a, b) =>
    new Date(a.dateSession).getTime() -
    new Date(b.dateSession).getTime());
}

estAujourdhuiCal(date: Date): boolean {
  const today = new Date();
  return date.getDate()     === today.getDate() &&
         date.getMonth()    === today.getMonth() &&
         date.getFullYear() === today.getFullYear();
}

estMoisCourant(date: Date): boolean {
  return date.getMonth() === this.calCurrentDate.getMonth() &&
         date.getFullYear() === this.calCurrentDate.getFullYear();
}

// Couleur par type de session
getSessionColor(type: string): string {
  return type === 'COURS'     ? '#4A7C7E'
       : type === 'QUIZ'      ? '#27ae60'
       : type === 'EVENEMENT' ? '#f39c12'
       : '#9B8B6E';
}

getSessionBg(type: string): string {
  return type === 'COURS'     ? 'rgba(74,124,126,.15)'
       : type === 'QUIZ'      ? 'rgba(39,174,96,.15)'
       : type === 'EVENEMENT' ? 'rgba(243,156,18,.15)'
       : 'rgba(155,139,110,.15)';
}

getSessionIcon(type: string): string {
  return type === 'COURS'     ? '📚'
       : type === 'QUIZ'      ? '✏️'
       : type === 'EVENEMENT' ? '📅'
       : '📌';
}

// Prochaines sessions (3 max)
get prochainesSessions(): any[] {
  const now = new Date();
  return this.sessions
    .filter(s => new Date(s.dateSession) > now && !s.isTerminee)
    .sort((a, b) =>
      new Date(a.dateSession).getTime() -
      new Date(b.dateSession).getTime())
    .slice(0, 4);
}

get sessionImminente(): any | null {
  return this.prochainesSessions[0] || null;
}

estBientot(s: any): boolean {
  const diff = new Date(s.dateSession).getTime() - Date.now();
  return diff > 0 && diff < 24 * 3600 * 1000;
}

// Statistiques hebdo/mensuel
get heuresSemaine(): number {
  const debut = new Date();
  debut.setDate(debut.getDate() - debut.getDay() + 1);
  debut.setHours(0, 0, 0, 0);
  const fin = new Date(debut);
  fin.setDate(fin.getDate() + 6);
  return Math.round(
    this.sessions
      .filter(s => {
        const d = new Date(s.dateSession);
        return d >= debut && d <= fin;
      })
      .reduce((acc, s) => acc + (s.dureeMinutes || 60), 0) / 60
  );
}

get heuresMois(): number {
  return Math.round(
    this.sessions
      .filter(s => {
        const d = new Date(s.dateSession);
        return d.getMonth()    === this.calCurrentDate.getMonth() &&
               d.getFullYear() === this.calCurrentDate.getFullYear();
      })
      .reduce((acc, s) => acc + (s.dureeMinutes || 60), 0) / 60
  );
}

// Modal ajouter
ouvrirModalAjouter(date?: Date) {
  this.sessionModalMode = 'ajouter';
  this.sessionEnEdition = null;
  this.sessionError     = '';
  this.sessionSuccess   = '';
  const d = date || new Date();
  const pad = (n: number) => n.toString().padStart(2, '0');
  this.sessionForm = {
    titre:        '',
    formationId:  null,
    dateSession:  `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`,
    heure:        '09:00',
    dureeMinutes: 60,
    typeSession:  'COURS',
    notes:        '',
    rappel24h:    true
  };
  this.showSessionModal = true;
  this.cdr.detectChanges();
}

// Modal modifier
ouvrirModalModifier(session: any, event: Event) {
  event.stopPropagation();
  this.sessionModalMode = 'modifier';
  this.sessionEnEdition = session;
  this.sessionError     = '';
  this.sessionSuccess   = '';
  const d   = new Date(session.dateSession);
  const pad = (n: number) => n.toString().padStart(2, '0');
  this.sessionForm = {
    titre:        session.titrePersonnalise,
    formationId:  session.formationId || null,
    dateSession:  `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`,
    heure:        `${pad(d.getHours())}:${pad(d.getMinutes())}`,
    dureeMinutes: session.dureeMinutes || 60,
    typeSession:  session.typeSession  || 'COURS',
    notes:        session.notes        || '',
    rappel24h:    session.rappel24h
  };
  this.showSessionModal = true;
  this.cdr.detectChanges();
}

fermerModalSession() {
  this.showSessionModal = false;
  this.sessionEnEdition = null;
  this.sessionError     = '';
  this.cdr.detectChanges();
}

// Construire dateSession ISO
get sessionDateISO(): string {
  return `${this.sessionForm.dateSession}T${this.sessionForm.heure}:00`;
}

// Récap lisible
get sessionRecap(): string {
  if (!this.sessionForm.dateSession || !this.sessionForm.heure) return '';
  const d = new Date(`${this.sessionForm.dateSession}T${this.sessionForm.heure}`);
  const h = Math.floor(this.sessionForm.dureeMinutes / 60);
  const m = this.sessionForm.dureeMinutes % 60;
  const duree = h > 0 ? `${h}h${m > 0 ? m + 'min' : ''}` : `${m}min`;
  return d.toLocaleDateString('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long'
  }) + ` à ${this.sessionForm.heure} (${duree})`;
}

sauvegarderSession() {
  if (!this.sessionForm.titre.trim()) {
    this.sessionError = 'Le titre est requis.'; return;
  }
  if (!this.sessionForm.dateSession) {
    this.sessionError = 'La date est requise.'; return;
  }

  this.sessionLoading = true;
  this.sessionError   = '';

  const payload = {
    titre:        this.sessionForm.titre,
    formationId:  this.sessionForm.formationId,
    dateSession:  this.sessionDateISO,
    dureeMinutes: this.sessionForm.dureeMinutes,
    typeSession:  this.sessionForm.typeSession,
    notes:        this.sessionForm.notes,
    rappel24h:    this.sessionForm.rappel24h
  };

  const req$ = this.sessionModalMode === 'ajouter'
    ? this.http.post<any>(
        `${this.api}/calendrier`, payload,
        { headers: this.headers() })
    : this.http.put<any>(
        `${this.api}/calendrier/${this.sessionEnEdition.id}`,
        payload, { headers: this.headers() });

  req$.subscribe({
    next: () => {
      this.sessionLoading = false;
      this.fermerModalSession();
      this.loadSessions();
    },
    error: err => {
      this.sessionLoading = false;
      this.sessionError   = err.error?.message || 'Erreur.';
      this.cdr.detectChanges();
    }
  });
}

supprimerSession(session: any, event: Event) {
  event.stopPropagation();
  if (!confirm(`Supprimer "${session.titrePersonnalise}" ?`)) return;
  this.http.delete(
    `${this.api}/calendrier/${session.id}`,
    { headers: this.headers() }
  ).subscribe({
    next:  () => this.loadSessions(),
    error: () => {}
  });
}

formatHeure(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`;
}

formatDuree(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h > 0 ? `${h}h${m > 0 ? m + 'min' : ''}` : `${m}min`;
}
loadProgressionData() {
    if (this.progressionFormations.length > 0) return;
    this.progressionLoading = true;
    this.progressionError   = '';

    const inscriptions = this.formations;
    if (inscriptions.length === 0) {
      this.progressionLoading = false;
      return;
    }

    const requests = inscriptions.map((f: any) => {
      const fid = f.formationId || f.id;
      return this.http.get<any>(
        `${this.api}/formations/${fid}/progression`,
        { headers: this.headers() }
      ).toPromise().catch(() => null);
    });

    Promise.all(requests).then((progressions: any[]) => {
      this.progressionFormations = inscriptions.map((f: any, i: number) => {
        const prog = progressions[i];
        const pct  = prog?.progression || 0;
        return {
          id:               f.formationId || f.id,
          titre:            f.titre,
          image:            f.imageCouverture,
          niveau:           f.niveau,
          progression:      pct,
          totalCours:       prog?.totalCours       || 0,
          coursTermines:    prog?.coursTermines     || 0,
          videosVues:       prog?.videosVues        || 0,
          documentsOuverts: prog?.documentsOuverts  || 0,
          quizPasses:       prog?.quizPasses        || 0,
          statut:           pct >= 100 ? 'TERMINE' : pct > 0 ? 'EN_COURS' : 'A_FAIRE',
          detailCours:      prog?.detailCours       || []
        };
      });

      const terminees = this.progressionFormations.filter(f => f.statut === 'TERMINE').length;
      const enCours   = this.progressionFormations.filter(f => f.statut === 'EN_COURS').length;
      const scores    = this.progressionFormations.filter(f => f.progression > 0).map(f => f.progression);

      this.progressionStatsGlobales = {
        totalFormations:     inscriptions.length,
        formationsTerminees: terminees,
        formationsEnCours:   enCours,
        scoreMoyen: scores.length > 0
          ? Math.round(scores.reduce((a: number, b: number) => a + b, 0) / scores.length)
          : 0
      };

      this.progressionLoading = false;
      this.cdr.detectChanges();
    });
  }

  getStatutLabel(statut: string): string {
    if (statut === 'TERMINE')  return 'Terminée';
    if (statut === 'EN_COURS') return 'En cours';
    return 'À faire';
  }
  // ══════════════════════════════════════════════════════
// US-033 — QUIZ FINAL APPRENANT
// ══════════════════════════════════════════════════════

private stopTimerQf() {
  if (this.qfTimerInterval) {
    clearInterval(this.qfTimerInterval);
    this.qfTimerInterval = null;
  }
}

ouvrirQuizFinal() {
  const fid = this.selectedFormation?.formationId || this.selectedFormation?.id;
  this.showQuizFinalModal   = true;
  this.qfEtape              = 'chargement';
  this.qfData               = null;
  this.qfQuestionIndex      = 0;
  this.qfReponses           = {};
  this.qfResultat           = null;
  this.qfConditionsAcceptees = false;
  this.showQfConfirm        = false;
  this.stopTimerQf();
  this.cdr.detectChanges();

  this.http.get<any>(
    `${this.api}/formations/${fid}/quiz-final`,
    { headers: this.headers() }
  ).subscribe({
    next: res => {
      if (!res.exists) {
        this.showQuizFinalModal = false;
        alert('Aucun quiz final disponible pour cette formation.');
        this.cdr.detectChanges();
        return;
      }
      // ← Stocker le formationId dans qfData pour s'en souvenir
  res.formationId = this.selectedFormation?.formationId || this.selectedFormation?.id;
  this.qfData   = res;
  this.qfEtape  = 'intro';
  this.cdr.detectChanges();
    },
    error: err => {
      this.showQuizFinalModal = false;
      alert(err.error?.message || 'Impossible de charger le quiz final.');
      this.cdr.detectChanges();
    }
  });
}

demarrerQuizFinal() {
  if (!this.qfConditionsAcceptees) return;
  this.qfEtape         = 'question';
  this.qfQuestionIndex = 0;
  this.qfReponses      = {};
  this.qfTempsTotal    = (this.qfData?.dureeMinutes || 20) * 60;
  this.qfTempsRestant  = this.qfTempsTotal;
  this.stopTimerQf();
  this.qfTimerInterval = setInterval(() => {
    this.qfTempsRestant--;
    if (this.qfTempsRestant <= 0) {
      this.stopTimerQf();
      this.confirmerSoumission();
    }
    this.cdr.detectChanges();
  }, 1000);
  this.cdr.detectChanges();
}

selectionnerReponseQf(questionId: number, optionId: number) {
  // Forcer en number pour éviter les problèmes de type string/number
  this.qfReponses[Number(questionId)] = Number(optionId);
  this.cdr.detectChanges();
}

soumettreQuizFinal() {
  const questions  = this.qfData?.questions || [];
  const nonRepondu = questions.filter((q: any) => !this.qfReponses[q.id]);
  if (nonRepondu.length > 0) {
    this.showQfConfirm = true;
    this.cdr.detectChanges();
    return;
  }
  this.confirmerSoumission();
}

confirmerSoumission() {
  this.showQfConfirm = false;
  this.stopTimerQf();
  this.qfSoumission  = true;
  this.cdr.detectChanges();

  const fid = this.qfData?.formationId 
              || this.selectedFormation?.formationId 
              || this.selectedFormation?.id;
  const tempsPasse = this.qfTempsTotal - this.qfTempsRestant;

  // ← CORRECTION : forcer les clés et valeurs en nombres entiers
  const reponsesNumeriques: { [key: number]: number } = {};
  Object.keys(this.qfReponses).forEach(k => {
    const questionId = parseInt(k, 10);
    const optionId   = parseInt(String(this.qfReponses[Number(k)]), 10);
    if (!isNaN(questionId) && !isNaN(optionId)) {
      reponsesNumeriques[questionId] = optionId;
    }
  });

  console.log('DEBUG fid:', fid, 'reponses brutes:', this.qfReponses, 'reponses converties:', reponsesNumeriques);

  this.http.post<any>(
    `${this.api}/formations/${fid}/quiz-final/soumettre`,
    { reponses: reponsesNumeriques, tempsPasse },
    { headers: this.headers() }
  ).subscribe({
    next: res => {
      this.qfResultat   = res;
      this.qfSoumission = false;
      this.qfEtape      = 'resultat';
     if (res.reussi) {
  this.genererConfettiQf();
  setTimeout(() => {
    this.http.get<any[]>(`${this.api}/certificats`, { headers: this.headers() })
      .subscribe({
        next: certs => {
          const fid = this.qfData?.formationId
                      || this.selectedFormation?.formationId
                      || this.selectedFormation?.id;
          this.qfCertificat = certs.find((c: any) => c.formationId === fid) || certs[0] || null;
          this.cdr.detectChanges();
        },
        error: () => {}
      });
  }, 1500);
}
      this.cdr.detectChanges();
    },
    error: err => {
      this.qfSoumission = false;
      console.error('Erreur soumettre détail:', err.error);
      alert(err.error?.message || 'Erreur lors de la soumission.');
      this.cdr.detectChanges();
    }
  });
}

retenterQuizFinal() {
  this.qfEtape              = 'intro';
  this.qfResultat           = null;
  this.qfReponses           = {};
  this.qfConditionsAcceptees = false;
  this.qfConfetti           = [];
  this.stopTimerQf();
  // Recharger les infos (tentatives mises à jour)
  this.ouvrirQuizFinal();
}

fermerQuizFinal() {
  this.showQuizFinalModal   = false;
  this.qfData               = null;
  this.qfResultat           = null;
  this.qfReponses           = {};
  this.qfEtape              = 'chargement';
  this.qfConditionsAcceptees = false;
  this.showQfConfirm        = false;
  this.qfConfetti           = [];
  this.stopTimerQf();
  this.cdr.detectChanges();
}

formatTempsRestantQf(): string {
  const h = Math.floor(this.qfTempsRestant / 3600);
  const m = Math.floor((this.qfTempsRestant % 3600) / 60);
  const s = this.qfTempsRestant % 60;
  const pad = (n: number) => n.toString().padStart(2, '0');
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`;
  return `${pad(m)}:${pad(s)}`;
}

getQfDashOffset(): number {
  const score = this.qfResultat?.score || 0;
  return 408.4 * (1 - score / 100);
}

genererConfettiQf() {
  const couleurs = ['#4A7C7E','#27ae60','#f39c12','#8B3A3A','#9b59b6','#2ecc71'];
  this.qfConfetti = Array.from({ length: 35 }, (_, i) => ({
    id:       i,
    left:     Math.random() * 100,
    delay:    Math.random() * 2,
    duration: 2 + Math.random() * 2,
    color:    couleurs[Math.floor(Math.random() * couleurs.length)],
    size:     6 + Math.random() * 8
  }));
}

getQfDotColor(index: number): string {
  if (!this.qfData?.questions) return '#E8E3DB';
  const q = this.qfData.questions[index];
  if (!q) return '#E8E3DB';
  if (index === this.qfQuestionIndex) return '#4A7C7E';
  return this.qfReponses[q.id] ? '#27ae60' : '#E8E3DB';
}
// ══════════════════════════════════════════════════
// US-048 — CERTIFICATS
// ══════════════════════════════════════════════════

loadCertificats() {
  this.certificatsLoading = true;
  this.http.get<any[]>(`${this.api}/certificats`, { headers: this.headers() })
    .subscribe({
      next: data => {
        this.certificats = data || [];
        this.certificatsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.certificats = [];
        this.certificatsLoading = false;
        this.cdr.detectChanges();
      }
    });
}

getScoreMoyenCerts(): number {
  if (!this.certificats.length) return 0;
  const scores = this.certificats
    .filter((c: any) => c.noteFinal != null)
    .map((c: any) => c.noteFinal);
  if (!scores.length) return 0;
  return scores.reduce((a: number, b: number) => a + b, 0) / scores.length;
}

downloadCertificat(cert: any) {
  cert._downloading = true;
  this.cdr.detectChanges();
  const token = localStorage.getItem('token');
  fetch(`${this.api}/certificats/${cert.id}/download`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  .then(res => {
    if (!res.ok) throw new Error('Erreur téléchargement');
    return res.blob();
  })
  .then(blob => {
    const url  = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href  = url;
    const num  = (cert.numeroCertificat || 'cert').replace('#', '').replace(/-/g, '_');
    link.download = `certificat_${num}.pdf`;
    link.click();
    window.URL.revokeObjectURL(url);
    cert._downloading = false;
    this.cdr.detectChanges();
  })
  .catch(() => {
    cert._downloading = false;
    alert('Erreur lors du téléchargement.');
    this.cdr.detectChanges();
  });
}

downloadCertificatFromModal() {
  if (!this.qfCertificat) return;
  this.qfCertDownloading = true;
  this.cdr.detectChanges();
  const token = localStorage.getItem('token');
  fetch(`${this.api}/certificats/${this.qfCertificat.id}/download`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  .then(r => r.blob())
  .then(blob => {
    const url  = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href  = url;
    const num  = (this.qfCertificat.numeroCertificat || 'cert').replace('#', '').replace(/-/g, '_');
    link.download = `certificat_${num}.pdf`;
    link.click();
    window.URL.revokeObjectURL(url);
    this.qfCertDownloading = false;
    this.cdr.detectChanges();
  })
  .catch(() => {
    this.qfCertDownloading = false;
    alert('Erreur téléchargement.');
    this.cdr.detectChanges();
  });
}
 // ══════════════════════════════════════════════════
  // US-050 — ENVOYER CERTIFICAT PAR EMAIL
  // ══════════════════════════════════════════════════

  certToast = {
    visible: false,
    message: '',
    type:    'success' as 'success' | 'error'
  };

  private showCertToast(message: string, type: 'success' | 'error' = 'success') {
    this.certToast = { visible: true, message, type };
    this.cdr.detectChanges();
    setTimeout(() => {
      this.certToast.visible = false;
      this.cdr.detectChanges();
    }, 4500);
  }

  envoyerCertificatEmail(cert: any) {
    cert._sending = true;
    this.cdr.detectChanges();

    this.http.post<any>(
      `${this.api}/certificats/${cert.id}/envoyer-email`,
      {},
      { headers: this.headers() }
    ).subscribe({
     next: () => { 
  cert._sending = false; 
  cert.estEnvoye = true;
  this.showCertToast(`Certificat renvoyé à ${this.apprenantUser?.email} ! Vérifiez votre boîte mail. 🎓`); 
},
      error: err => {
        cert._sending = false;
        this.showCertToast(
          err.error?.message || 'Erreur lors de l\'envoi par email.', 'error'
        );
      }
    });
  }
  
  // ══════════════════════════════════════════════════
  // US-059 — PARTAGER CERTIFICAT SUR LINKEDIN
  // ══════════════════════════════════════════════════
 
  showLinkedInModal  = false;
  linkedInCert: any  = null;
  linkedInData: any  = null;   // { linkedinUrl, textePost, ... }
  linkedInLoading    = false;
  linkedInCopied     = false;
 
 ouvrirModalLinkedIn(cert: any) {
    this.linkedInCert      = cert;
    this.linkedInData      = null;
    this.linkedInLoading   = true;
    this.linkedInCopied    = false;
    this.linkedInQrUrl     = null;
    this.linkedInQrLoading = true;
    this.showLinkedInModal = true;
    this.cdr.detectChanges();
 
    // 1. Préparer le texte du post (appel backend)
    this.http.post<any>(
      `${this.api}/certificats/${cert.id}/partager-linkedin`,
      {},
      { headers: this.headers() }
    ).subscribe({
      next: data => {
        this.linkedInData    = data;
        this.linkedInLoading = false;
        cert.partageLinkedIn = true;
 
        // 2. Charger le QR code en parallèle
        this.loadQrCode(cert.id);
        this.cdr.detectChanges();
      },
      error: err => {
        this.linkedInLoading   = false;
        this.linkedInQrLoading = false;
        this.showLinkedInModal = false;
        this.showCertToast(err.error?.message || 'Erreur LinkedIn.', 'error');
        this.cdr.detectChanges();
      }
    });
  }
  loadQrCode(certId: number) {
    this.linkedInQrLoading = true;
    const token = localStorage.getItem('token');
    fetch(`${this.api}/certificats/${certId}/qrcode`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    .then(r => r.blob())
    .then(blob => {
      this.linkedInQrUrl     = URL.createObjectURL(blob);
      this.linkedInQrLoading = false;
      this.cdr.detectChanges();
    })
    .catch(() => {
      this.linkedInQrLoading = false;
      this.cdr.detectChanges();
    });
  }
 
  fermerModalLinkedIn() {
    this.showLinkedInModal = false;
    this.linkedInCert      = null;
    this.linkedInData      = null;
    this.cdr.detectChanges();
  }
 
 partagerSurLinkedIn() {
    if (!this.linkedInData?.linkedinUrl) return;
 
    // 1. Copie automatique du texte
    if (this.linkedInData?.textePost) {
      navigator.clipboard.writeText(this.linkedInData.textePost).catch(() => {});
    }
 
    // 2. Animation envol (600ms) PUIS ouverture LinkedIn
    this.linkedInAnimating = true;
    this.cdr.detectChanges();
 
    setTimeout(() => {
      this.linkedInAnimating = false;
      this.cdr.detectChanges();
      window.open(this.linkedInData.linkedinUrl, '_blank', 'width=650,height=650');
    }, 700);
  }
 
  copierTexteLinkedIn() {
    if (!this.linkedInData?.textePost) return;
    navigator.clipboard.writeText(this.linkedInData.textePost).then(() => {
      this.linkedInCopied = true;
      this.cdr.detectChanges();
      setTimeout(() => { this.linkedInCopied = false; this.cdr.detectChanges(); }, 2500);
    });
  }
  partagerLinkedInFromModal() {
    if (!this.qfCertificat) return;
    this.fermerQuizFinal();
    setTimeout(() => {
      const cert = this.certificats.find((c: any) => c.id === this.qfCertificat?.id)
                   || this.qfCertificat;
      this.ouvrirModalLinkedIn(cert);
    }, 300);
  }
}