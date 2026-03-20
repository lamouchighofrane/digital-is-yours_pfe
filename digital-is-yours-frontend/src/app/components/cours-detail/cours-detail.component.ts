import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-cours-detail',
  templateUrl: './cours-detail.component.html',
  styleUrls: ['./cours-detail.component.css']
})
export class CoursDetailComponent implements OnInit, OnDestroy {

  cours: any = null;
  formation: any = null;
  tousLesCours: any[] = [];
  documents: any[] = [];
  documentsLoading = false;
  loading = true;
  error = '';

  formationId!: number;
  coursId!: number;

  // ── Marquer comme vu ──────────────────────────────────────────
  marquerCommeVuLoading = false;
  marquerCommeVuSuccess = false;
  coursVu        = false;
  videoVue       = false;
  documentOuvert = false;
  quizPasse      = false;

  // ── Mini-quiz ──────────────────────────────────────────────────
  showMiniQuizModal   = false;
  mqEtape: 'chargement' | 'intro' | 'question' | 'resultat' = 'chargement';
  mqQuiz: any         = null;
  mqQuestionIndex     = 0;
  mqReponses: { [questionId: number]: number } = {};
  mqResultat: any     = null;
  mqSoumission        = false;
  mqTemps             = 0;
  mqTempsTotal        = 0;
  mqConfetti: any[]   = [];
  private mqTimerInterval: any = null;

  private api = 'http://localhost:8080/api/apprenant';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('token');
    if (!token) { this.router.navigate(['/login']); return; }

    this.formationId = +this.route.snapshot.paramMap.get('formationId')!;
    this.coursId     = +this.route.snapshot.paramMap.get('coursId')!;

    this.loadCours();
  }

  ngOnDestroy() {
    this.stopChrono();
  }

  private headers(): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${localStorage.getItem('token')}`
    });
  }

  // ══════════════════════════════════════════════════════
  // CHARGEMENT COURS
  // ══════════════════════════════════════════════════════

  loadCours() {
    this.loading = true;
    this.error   = '';

    this.http.get<any>(
      `${this.api}/formations/${this.formationId}/cours`,
      { headers: this.headers() }
    ).subscribe({
      next: (res: any) => {
        this.tousLesCours = res.cours || [];
        this.cours = this.tousLesCours.find((c: any) => c.id === this.coursId);

        this.formation = {
          titre: this.route.snapshot.queryParamMap.get('titre') || 'Formation'
        };

        if (!this.cours) {
          this.error = 'Cours introuvable.';
          this.loading = false;
          return;
        }

        // Initialiser les états depuis la réponse API (persistés en BD)
        this.videoVue       = this.cours.videoVue       || this.cours.estTermine;
        this.documentOuvert = this.cours.documentOuvert || this.cours.estTermine;
        this.quizPasse      = this.cours.quizPasse      || this.cours.estTermine;
        this.coursVu        = this.videoVue;

        this.loading = false;
        this.loadDocuments();
      },
      error: (err: any) => {
        this.error   = err.error?.message || 'Impossible de charger le cours.';
        this.loading = false;
      }
    });
  }

  loadDocuments() {
    this.documentsLoading = true;
    this.http.get<any>(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/documents`,
      { headers: this.headers() }
    ).subscribe({
      next: (res: any) => {
        this.documents        = res.documents || [];
        this.documentsLoading = false;
      },
      error: () => {
        this.documents        = [];
        this.documentsLoading = false;
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // NAVIGATION
  // ══════════════════════════════════════════════════════

  retour() {
    this.router.navigate(['/apprenant/dashboard'], {
      queryParams: { formationId: this.formationId }
    });
  }

  allerAuCours(c: any, i: number) {
    if (i > 0 && !this.tousLesCours[i - 1].estTermine && !c.estTermine) return;
    this.router.navigate(['/apprenant/cours', this.formationId, c.id]);
  }

  // ══════════════════════════════════════════════════════
  // MARQUER VIDÉO VUE
  // ══════════════════════════════════════════════════════

  marquerCommeVu() {
    if (this.marquerCommeVuLoading || this.videoVue || this.cours?.estTermine) return;
    this.marquerCommeVuLoading = true;

    this.http.post<any>(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/marquer-video-vue`,
      {},
      { headers: this.headers() }
    ).subscribe({
      next: (res: any) => {
        this.marquerCommeVuLoading = false;
        this.marquerCommeVuSuccess = true;
        this.videoVue       = res.videoVue;
        this.documentOuvert = res.documentOuvert;
        this.quizPasse      = res.quizPasse;
        this.coursVu        = true;
        if (res.statut === 'TERMINE' && this.cours) {
          this.cours.estTermine = true;
        }
      },
      error: () => { this.marquerCommeVuLoading = false; }
    });
  }

  // ══════════════════════════════════════════════════════
  // OUVRIR DOCUMENT → marquer document ouvert
  // ══════════════════════════════════════════════════════

  ouvrirDocument(doc: any) {
    const token = localStorage.getItem('token');
    const url = `${this.api}/formations/${this.formationId}/cours/${this.coursId}/documents/${doc.id}/download`;

    // 1. Ouvrir dans un nouvel onglet
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then((res: Response) => res.blob())
      .then((blob: Blob) => {
        const blobUrl = window.URL.createObjectURL(blob);
        window.open(blobUrl, '_blank');
        window.URL.revokeObjectURL(blobUrl);
      });

    // 2. Marquer document ouvert silencieusement
    if (!this.documentOuvert) {
      this.http.post<any>(
        `${this.api}/formations/${this.formationId}/cours/${this.coursId}/marquer-document-ouvert`,
        {},
        { headers: this.headers() }
      ).subscribe({
        next: (res: any) => {
          this.documentOuvert = res.documentOuvert;
          this.quizPasse      = res.quizPasse;
          if (res.statut === 'TERMINE' && this.cours) {
            this.cours.estTermine = true;
          }
        },
        error: () => {}
      });
    }
  }

  downloadDocument(doc: any) {
    const token = localStorage.getItem('token');
    fetch(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/documents/${doc.id}/download`,
      { headers: { Authorization: `Bearer ${token}` } }
    ).then((res: Response) => res.blob()).then((blob: Blob) => {
      const objectUrl = window.URL.createObjectURL(blob);
      const a         = document.createElement('a');
      a.href          = objectUrl;
      a.download      = doc.nomFichier || doc.titre;
      a.click();
      window.URL.revokeObjectURL(objectUrl);
    });
  }

  // ══════════════════════════════════════════════════════
  // TERMINER APRÈS QUIZ
  // ══════════════════════════════════════════════════════

  terminerCours() {
    this.http.post<any>(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/terminer-apres-quiz`,
      {},
      { headers: this.headers() }
    ).subscribe({
      next: (res: any) => {
        this.quizPasse = res.quizPasse;
        if (res.statut === 'TERMINE') {
          if (this.cours) this.cours.estTermine = true;
          const idx = this.tousLesCours.findIndex((c: any) => c.id === this.coursId);
          if (idx >= 0) this.tousLesCours[idx].estTermine = true;
        }
        // Recharger silencieusement
        this.http.get<any>(
          `${this.api}/formations/${this.formationId}/cours`,
          { headers: this.headers() }
        ).subscribe({
          next: (r: any) => {
            this.tousLesCours = r.cours || [];
            const updated = this.tousLesCours.find((c: any) => c.id === this.coursId);
            if (updated) this.cours = updated;
          },
          error: () => {}
        });
      },
      error: () => {}
    });
  }

  // ══════════════════════════════════════════════════════
  // MINI-QUIZ
  // ══════════════════════════════════════════════════════

  ouvrirMiniQuiz() {
    if (!this.coursVu && !this.cours?.estTermine) return;
    if (!this.cours?.hasQuiz) return;

    this.mqQuiz          = null;
    this.mqResultat      = null;
    this.mqReponses      = {};
    this.mqConfetti      = [];
    this.mqQuestionIndex = 0;
    this.mqSoumission    = false;
    this.mqEtape         = 'chargement';
    this.showMiniQuizModal = true;

    this.http.get<any>(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/mini-quiz`,
      { headers: this.headers() }
    ).subscribe({
      next: (quiz: any) => {
        if (!quiz || !quiz.exists) {
          this.showMiniQuizModal = false;
          return;
        }
        this.mqQuiz  = quiz;
        this.mqEtape = 'intro';
      },
      error: () => { this.showMiniQuizModal = false; }
    });
  }

  demarrerQuiz() {
    this.mqQuestionIndex = 0;
    this.mqReponses      = {};
    this.mqTemps         = 0;
    this.mqEtape         = 'question';
    this.demarrerChrono();
  }

  private demarrerChrono() {
    this.stopChrono();
    this.mqTimerInterval = setInterval(() => { this.mqTemps++; }, 1000);
  }

  private stopChrono() {
    if (this.mqTimerInterval) {
      clearInterval(this.mqTimerInterval);
      this.mqTimerInterval = null;
    }
  }

  get mqQuestionCourante(): any {
    return this.mqQuiz?.questions?.[this.mqQuestionIndex] || null;
  }

  get mqTotalQuestions(): number {
    return this.mqQuiz?.questions?.length || 0;
  }

  get mqNbReponsesSelectionnees(): number {
    return Object.keys(this.mqReponses).length;
  }

  mqReponseSelectionnee(questionId: number): number | undefined {
    return this.mqReponses[questionId];
  }

  selectionnerReponse(questionId: number, optionId: number) {
    this.mqReponses[questionId] = optionId;
  }

  questionSuivante() {
    if (this.mqQuestionIndex < this.mqTotalQuestions - 1) this.mqQuestionIndex++;
  }

  questionPrecedente() {
    if (this.mqQuestionIndex > 0) this.mqQuestionIndex--;
  }

  allerAQuestion(index: number) {
    this.mqQuestionIndex = index;
  }

  soumettreQuiz() {
    if (!this.formationId || !this.coursId) return;
    this.stopChrono();
    this.mqTempsTotal = this.mqTemps;
    this.mqSoumission = true;

    this.http.post<any>(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/mini-quiz/soumettre`,
      { reponses: this.mqReponses, tempsPasse: this.mqTempsTotal },
      { headers: this.headers() }
    ).subscribe({
      next: (resultat: any) => {
        this.mqResultat   = resultat;
        this.mqSoumission = false;
        this.mqEtape      = 'resultat';
        if (resultat.reussi) this.genererConfetti();
        this.terminerCours();
        setTimeout(() => {
          const modal = document.querySelector('.mq-modal');
          if (modal) modal.scrollTop = 0;
        }, 100);
      },
      error: () => { this.mqSoumission = false; }
    });
  }

  getMqDashOffset(): number {
    const score = this.mqResultat?.score || 0;
    return 2 * Math.PI * 52 * (1 - score / 100);
  }

  private genererConfetti() {
    const couleurs = ['#4A7C7E', '#27ae60', '#f39c12', '#e74c3c', '#9b59b6', '#3498db'];
    this.mqConfetti = Array.from({ length: 30 }, () => ({
      left:  `${Math.random() * 100}%`,
      delay: `${Math.random() * 1.5}s`,
      color: couleurs[Math.floor(Math.random() * couleurs.length)]
    }));
  }

  recommencerQuiz() {
    this.mqResultat      = null;
    this.mqConfetti      = [];
    this.mqReponses      = {};
    this.mqQuestionIndex = 0;
    this.mqTemps         = 0;
    this.mqEtape         = 'intro';
  }

  allerCoursSuivant() {
    this.fermerMiniQuiz();
    const indexCourant = this.tousLesCours.findIndex((c: any) => c.id === this.coursId);
    const coursSuivant = this.tousLesCours[indexCourant + 1];
    if (coursSuivant) {
      this.router.navigate(['/apprenant/cours', this.formationId, coursSuivant.id]);
    } else {
      this.retour();
    }
  }

  fermerMiniQuiz() {
    this.stopChrono();
    this.showMiniQuizModal = false;
    this.mqQuiz            = null;
    this.mqResultat        = null;
    this.mqConfetti        = [];
    this.mqSoumission      = false;
  }

  // ══════════════════════════════════════════════════════
  // UTILITAIRES
  // ══════════════════════════════════════════════════════

  formatTemps(secondes: number): string {
    if (!secondes && secondes !== 0) return '0:00';
    const m = Math.floor(secondes / 60);
    const s = secondes % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  getYoutubeEmbedUrl(url: string): SafeResourceUrl {
    if (!url) return '';
    const m = url.match(
      /(?:youtube\.com\/(?:watch\?v=|shorts\/|embed\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/
    );
    const embedUrl = m ? `https://www.youtube.com/embed/${m[1]}?autoplay=0&rel=0` : '';
    return this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl);
  }

  getVideoUrl(): SafeResourceUrl {
    if (!this.cours) return '';
    const url = `http://localhost:8080/api/apprenant/cours/${this.coursId}/video/stream/${this.cours.videoUrl}?formationId=${this.formationId}`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  getDocIconLabel(type: string): string {
    if (!type) return 'FILE';
    if (type.includes('pdf'))        return 'PDF';
    if (type.includes('word'))       return 'DOC';
    if (type.includes('powerpoint')) return 'PPT';
    if (type.includes('excel'))      return 'XLS';
    if (type.includes('image'))      return 'IMG';
    return 'FILE';
  }

  formatTailleDoc(bytes: number): string {
    if (!bytes) return '';
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  formatDuree(min: number): string {
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h > 0 ? `${h}h${m > 0 ? m + 'min' : ''}` : `${m}min`;
  }
}