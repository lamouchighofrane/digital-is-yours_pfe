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

  activeSection: 'dashboard' | 'mes-formations' | 'profil' | 'cours' = 'dashboard';
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

  photoPreview: string | null = null;
  photoUploading = false;
  competences: string[] = [];
  competenceInput = '';
  reseauxSociaux = { linkedin: '', twitter: '', portfolio: '', github: '' };

  // ── Cours ──────────────────────────────────────────────────
  selectedFormation: any = null;
  cours: any[] = [];
  coursLoading = false;
  coursStats = { total: 0, publies: 0, brouillons: 0 };
  coursSearch = '';
  coursFilterStatut = '';
  showCoursModal = false;
  coursModalMode: 'create' | 'edit' = 'create';
  coursSaving = false;
  coursToast = '';
  coursToastType: 'success' | 'error' = 'success';
  coursForm: any = {
    titre: '', description: '', dureeEstimee: 0,
    ordre: null, objectifs: '', statut: 'BROUILLON'
  };
  Math = Math;
  private editingCoursId: number | null = null;

  // ── Modal tab ──────────────────────────────────────────────
  coursModalTab: 'infos' | 'videos' | 'documents' | 'mini-quiz' = 'infos';

  // ── Mini-quiz : état général ───────────────────────────────
  miniQuiz: any = null;
  miniQuizLoading = false;
  miniQuizExists = false;
  miniQuizGenerating = false;
  showMiniQuizModal = false;
  miniQuizParams = {
    nombreQuestions: 5,
    difficulte: 'MOYEN',
    inclureDefinitions: true,
    inclureCasPratiques: true
  };
  miniQuizContexte: any = null;

  // ── Mini-quiz : édition inline question ───────────────────
  mqEditingId: number | null = null;
  mqEditForm: any = null;
  mqSaving = false;
  mqEditError = '';

  // ── Mini-quiz : suppression avec confirmation ─────────────
  showDeleteQuestionModal = false;
  mqDeleteQuestion: any = null;
  mqDeleteQuestionIndex = 0;
  mqDeleteSaving = false;

  // ── Mini-quiz : ajout manuel d'une question ───────────────
  showAddQuestionModal = false;
  mqAddSaving = false;
  mqAddError = '';
  mqNewQuestion: any = {
    texte: '',
    explication: '',
    options: [
      { ordre: 'A', texte: '', estCorrecte: true  },
      { ordre: 'B', texte: '', estCorrecte: false },
      { ordre: 'C', texte: '', estCorrecte: false },
      { ordre: 'D', texte: '', estCorrecte: false },
    ]
  };

  // ── Vidéo ─────────────────────────────────────────────────
  videoType: string | null = null;
  videoUrl: string | null = null;
  videoUploading = false;
  videoUploadProgress = 0;
  videoUploadingName = '';
  videoDragOver = false;
  youtubeUrlInput = '';
  videoYoutubeAdding = false;
  videoDeleting = false;
  private uploadXhr: XMLHttpRequest | null = null;
  private currentCoursId: number | null = null;

  // ── Drag & Drop réordonnancement cours ────────────────────
  dragIndex: number | null = null;
  dragOverIndex: number | null = null;
  isDragging = false;

  // ── Documents ──────────────────────────────────────────────
  documents: any[] = [];
  docLoading = false;
  docTotalTaille = 0;
  docDragOver = false;
  docUploading: { name: string; progress: number }[] = [];

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
    this.uploadXhr?.abort();
    this.uploadXhr = null;
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('formateur_token')}` });
  }

  // ── Init forms ─────────────────────────────────────────────
  private initForms() {
    const rs = this.formateurUser?.reseauxSociaux || {};
    this.reseauxSociaux = {
      linkedin:  rs.linkedin  || '',
      twitter:   rs.twitter   || '',
      portfolio: rs.portfolio || '',
      github:    rs.github    || '',
    };
    this.competences = Array.isArray(this.formateurUser?.competences)
      ? [...this.formateurUser.competences] : [];
    this.photoPreview = this.formateurUser?.photo || null;

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

  setSection(section: 'dashboard' | 'mes-formations' | 'profil' | 'cours') {
    this.activeSection = section;
    if (section === 'mes-formations') this.loadFormations();
    if (section === 'profil') this.loadProfil();
    if (section === 'cours') {
      this.selectedFormation = null;
      this.cours = [];
      this.coursStats = { total: 0, publies: 0, brouillons: 0 };
      this.loadFormations();
    }
    this.closeNotifPanel();
  }

  retourChoixFormation() {
    this.selectedFormation = null;
    this.cours = [];
    this.coursStats = { total: 0, publies: 0, brouillons: 0 };
    this.cdr.detectChanges();
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

  // ── Cours ──────────────────────────────────────────────────
  voirCours(formation: any) {
    this.selectedFormation = formation;
    this.activeSection = 'cours';
    this.loadCours();
    this.closeNotifPanel();
  }

  loadCours() {
    if (!this.selectedFormation) return;
    this.coursLoading = true;
    this.http.get<any[]>(`${this.api}/formations/${this.selectedFormation.id}/cours`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.cours = d || [];
          this.updateCoursStats();
          this.coursLoading = false;
          this.cdr.detectChanges();
          this.loadNbDocumentsForAllCours();
          this.loadQuizStatusForAllCours(); // ← NOUVEAU : charge le statut quiz pour chaque cours
        },
        error: () => { this.cours = []; this.coursLoading = false; this.cdr.detectChanges(); }
      });
  }

  loadNbDocumentsForAllCours() {
    if (!this.selectedFormation || !this.cours.length) return;
    this.cours.forEach(cours => {
      this.http.get<any>(
        `${this.api}/formations/${this.selectedFormation.id}/cours/${cours.id}/documents`,
        { headers: this.headers() }
      ).subscribe({
        next: (res) => {
          cours.nbDocuments = (res.documents || []).length;
          this.cdr.detectChanges();
        },
        error: () => { cours.nbDocuments = 0; }
      });
    });
  }

  // ── NOUVEAU : charge le statut quiz (hasQuiz) pour chaque cours de la liste ──
  loadQuizStatusForAllCours() {
    if (!this.selectedFormation || !this.cours.length) return;
    this.cours.forEach(cours => {
      this.http.get<any>(
        `${this.api}/formations/${this.selectedFormation.id}/cours/${cours.id}/mini-quiz`,
        { headers: this.headers() }
      ).subscribe({
        next: (res) => {
          cours.hasQuiz = res && res.exists === true;
          this.cdr.detectChanges();
        },
        error: () => { cours.hasQuiz = false; }
      });
    });
  }

  updateCoursStats() {
    this.coursStats.total = this.cours.length;
    this.coursStats.publies = this.cours.filter(c => c.statut === 'PUBLIE').length;
    this.coursStats.brouillons = this.cours.filter(c => c.statut === 'BROUILLON').length;
  }

  get filteredCours(): any[] {
    return this.cours.filter(c => {
      const matchSearch = !this.coursSearch ||
        c.titre?.toLowerCase().includes(this.coursSearch.toLowerCase()) ||
        c.description?.toLowerCase().includes(this.coursSearch.toLowerCase());
      const matchStatut = !this.coursFilterStatut || c.statut === this.coursFilterStatut;
      return matchSearch && matchStatut;
    });
  }

  openCoursModal(cours?: any) {
    this.coursModalTab = 'infos';
    this.youtubeUrlInput = '';
    this.videoUploading = false;
    this.videoUploadProgress = 0;
    this.videoDragOver = false;

    // Réinitialiser les états mini-quiz
    this.mqEditingId = null;
    this.mqEditForm = null;
    this.mqEditError = '';
    this.showDeleteQuestionModal = false;
    this.mqDeleteQuestion = null;
    this.showAddQuestionModal = false;
    this.mqAddError = '';

    if (cours) {
      this.coursModalMode = 'edit';
      this.editingCoursId = cours.id;
      this.currentCoursId = cours.id;
      this.coursForm = {
        titre: cours.titre || '',
        description: cours.description || '',
        dureeEstimee: cours.dureeEstimee || 0,
        ordre: cours.ordre || null,
        objectifs: cours.objectifs || '',
        statut: cours.statut || 'BROUILLON'
      };
      this.videoType = cours.videoType || null;
      this.videoUrl  = cours.videoUrl  || null;
      this.documents = [];
      this.docTotalTaille = 0;
      this.docUploading = [];

      // ── CORRECTION : pré-charger le statut quiz depuis la propriété hasQuiz
      //    du cours (déjà chargée par loadQuizStatusForAllCours).
      //    On remet miniQuiz à null pour forcer un rechargement propre
      //    UNIQUEMENT quand l'utilisateur clique sur l'onglet Mini-quiz.
      this.miniQuiz = null;
      this.miniQuizContexte = null;
      this.miniQuizExists = cours.hasQuiz === true; // ← affiche ✓ sur l'onglet dès l'ouverture

    } else {
      this.coursModalMode = 'create';
      this.editingCoursId = null;
      this.currentCoursId = null;
      this.coursForm = {
        titre: '', description: '', dureeEstimee: 0,
        ordre: null, objectifs: '', statut: 'BROUILLON'
      };
      this.videoType = null;
      this.videoUrl  = null;
      this.documents = [];
      this.docTotalTaille = 0;
      this.docUploading = [];
      this.miniQuiz = null;
      this.miniQuizExists = false;
      this.miniQuizContexte = null;
    }
    this.showCoursModal = true;
    this.cdr.detectChanges();
  }

  onDocTabClick() {
    if (this.coursModalMode !== 'create') {
      this.coursModalTab = 'documents';
      if (this.documents.length === 0 && !this.docLoading) {
        this.loadDocuments();
      }
    }
  }

  saveCours() {
    if (!this.coursForm.titre?.trim() || !this.selectedFormation) return;
    this.coursSaving = true;
    const payload = { ...this.coursForm };

    if (this.coursModalMode === 'create') {
      this.http.post<any>(`${this.api}/formations/${this.selectedFormation.id}/cours`, payload, { headers: this.headers() })
        .subscribe({
          next: (created) => {
            this.coursSaving = false;
            this.coursModalMode = 'edit';
            this.editingCoursId = created.id;
            this.currentCoursId = created.id;
            this.videoType = created.videoType || null;
            this.videoUrl  = created.videoUrl  || null;
            this.coursModalTab = 'videos';
            this.loadCours();
            this.showToast('Cours créé ! Ajoutez maintenant une vidéo.', 'success');
            this.cdr.detectChanges();
          },
          error: err => {
            this.coursSaving = false;
            this.showToast(err.error?.message || 'Erreur lors de la création.', 'error');
          }
        });
    } else {
      this.http.put<any>(`${this.api}/formations/${this.selectedFormation.id}/cours/${this.editingCoursId}`, payload, { headers: this.headers() })
        .subscribe({
          next: () => {
            this.coursSaving = false;
            this.loadCours();
            this.showToast('Cours modifié avec succès !', 'success');
            this.cdr.detectChanges();
          },
          error: err => {
            this.coursSaving = false;
            this.showToast(err.error?.message || 'Erreur lors de la modification.', 'error');
          }
        });
    }
  }

  toggleCoursStatut(cours: any) {
    this.http.patch(`${this.api}/formations/${this.selectedFormation.id}/cours/${cours.id}/toggle-statut`, {}, { headers: this.headers() })
      .subscribe({
        next: () => {
          cours.statut = cours.statut === 'PUBLIE' ? 'BROUILLON' : 'PUBLIE';
          this.updateCoursStats();
          this.showToast(`Cours ${cours.statut === 'PUBLIE' ? 'publié' : 'dépublié'} !`, 'success');
          this.cdr.detectChanges();
        },
        error: () => this.showToast('Erreur lors du changement de statut.', 'error')
      });
  }

  deleteCours(cours: any) {
    if (!confirm(`Supprimer le cours "${cours.titre}" ?`)) return;
    this.http.delete(`${this.api}/formations/${this.selectedFormation.id}/cours/${cours.id}`, { headers: this.headers() })
      .subscribe({
        next: () => {
          this.cours = this.cours.filter(c => c.id !== cours.id);
          this.updateCoursStats();
          this.showToast('Cours supprimé.', 'success');
          this.cdr.detectChanges();
        },
        error: () => this.showToast('Erreur lors de la suppression.', 'error')
      });
  }

  // ══ DRAG & DROP RÉORDONNANCEMENT ═══════════════════════════

  onCoursDragStart(event: DragEvent, index: number) {
    this.dragIndex = index;
    this.isDragging = true;
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', String(index));
    }
    this.cdr.detectChanges();
  }

  onCoursDragOver(event: DragEvent, index: number) {
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
    if (this.dragOverIndex !== index) {
      this.dragOverIndex = index;
      this.cdr.detectChanges();
    }
  }

  onCoursDragLeave(event: DragEvent) {
    const related = event.relatedTarget as HTMLElement;
    if (!related || !related.closest?.('.cours-card')) {
      this.dragOverIndex = null;
      this.cdr.detectChanges();
    }
  }

  onCoursDrop(event: DragEvent, dropIndex: number) {
    event.preventDefault();
    event.stopPropagation();

    const dragIdx = this.dragIndex;
    if (dragIdx === null || dragIdx === dropIndex) {
      this.resetDragState();
      return;
    }

    const draggedCours = this.filteredCours[dragIdx];
    const targetCours  = this.filteredCours[dropIndex];

    const realDragIdx   = this.cours.findIndex(c => c.id === draggedCours.id);
    const realTargetIdx = this.cours.findIndex(c => c.id === targetCours.id);

    if (realDragIdx === -1 || realTargetIdx === -1) {
      this.resetDragState();
      return;
    }

    const newCours = [...this.cours];
    const [removed] = newCours.splice(realDragIdx, 1);
    newCours.splice(realTargetIdx, 0, removed);

    newCours.forEach((c, idx) => c.ordre = idx + 1);
    this.cours = newCours;
    this.resetDragState();
    this.cdr.detectChanges();

    this.sauvegarderOrdre();
  }

  onCoursDragEnd() {
    this.resetDragState();
  }

  private resetDragState() {
    this.dragIndex = null;
    this.dragOverIndex = null;
    this.isDragging = false;
    this.cdr.detectChanges();
  }

  sauvegarderOrdre() {
    if (!this.selectedFormation) return;
    const ordres = this.cours.map(c => ({ id: c.id, ordre: c.ordre }));
    this.http.patch(
      `${this.api}/formations/${this.selectedFormation.id}/cours/reordonner`,
      { ordres },
      { headers: this.headers() }
    ).subscribe({
      next: () => this.showToast('Ordre des cours mis à jour !', 'success'),
      error: () => {
        this.showToast('Erreur lors de la sauvegarde de l\'ordre.', 'error');
        this.loadCours();
      }
    });
  }

  formatDuree(minutes: number): string {
    if (!minutes) return '';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0 && m > 0) return `${h}h ${m}min`;
    if (h > 0) return `${h}h`;
    return `${m}min`;
  }

  // ══ VIDÉO ═══════════════════════════════════════════════════

  onVideoDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.videoDragOver = true;
  }

  onVideoDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.videoDragOver = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) this.uploadVideoFile(files[0]);
  }

  onVideoFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.uploadVideoFile(file);
  }

  uploadVideoFile(file: File) {
    if (!this.currentCoursId || !this.selectedFormation) return;
    const allowed = ['video/mp4','video/avi','video/quicktime','video/x-msvideo','video/webm'];
    if (!allowed.includes(file.type)) {
      this.showToast('Format non supporté. Utilisez MP4, AVI, MOV ou WebM.', 'error'); return;
    }
    if (file.size > 1 * 1024 * 1024 * 1024) {
      this.showToast('Fichier trop volumineux. Maximum 1 GB.', 'error'); return;
    }

    this.videoUploading = true;
    this.videoUploadProgress = 0;
    this.videoUploadingName = file.name;
    this.cdr.detectChanges();

    const formData = new FormData();
    formData.append('fichier', file);
    const token = localStorage.getItem('formateur_token');
    const xhr = new XMLHttpRequest();
    this.uploadXhr = xhr;

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        this.videoUploadProgress = Math.round((e.loaded / e.total) * 100);
        this.cdr.detectChanges();
      }
    };
    xhr.onload = () => {
      this.videoUploading = false;
      this.uploadXhr = null;
      if (xhr.status === 200) {
        const updated = JSON.parse(xhr.responseText);
        this.videoType = updated.videoType;
        this.videoUrl  = updated.videoUrl;
        this.updateCoursInList(updated);
        this.showToast('Vidéo uploadée avec succès !', 'success');
      } else {
        try { this.showToast(JSON.parse(xhr.responseText).message || 'Erreur upload.', 'error'); }
        catch { this.showToast('Erreur lors de l\'upload.', 'error'); }
      }
      this.cdr.detectChanges();
    };
    xhr.onerror = () => {
      this.videoUploading = false;
      this.uploadXhr = null;
      this.showToast('Erreur réseau lors de l\'upload.', 'error');
      this.cdr.detectChanges();
    };
    xhr.open('POST', `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/video/upload`);
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    xhr.send(formData);
  }

  cancelVideoUpload() {
    this.uploadXhr?.abort();
    this.uploadXhr = null;
    this.videoUploading = false;
    this.videoUploadProgress = 0;
    this.cdr.detectChanges();
  }

  ajouterYoutube() {
    if (!this.youtubeUrlInput.trim() || !this.currentCoursId || !this.selectedFormation) return;
    this.videoYoutubeAdding = true;
    this.http.post<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/video/youtube`,
      { url: this.youtubeUrlInput.trim() },
      { headers: this.headers() }
    ).subscribe({
      next: updated => {
        this.videoType = updated.videoType;
        this.videoUrl  = updated.videoUrl;
        this.youtubeUrlInput = '';
        this.videoYoutubeAdding = false;
        this.updateCoursInList(updated);
        this.showToast('Vidéo YouTube associée au cours !', 'success');
        this.cdr.detectChanges();
      },
      error: err => {
        this.videoYoutubeAdding = false;
        this.showToast(err.error?.message || 'URL YouTube invalide.', 'error');
        this.cdr.detectChanges();
      }
    });
  }

  supprimerVideo() {
    if (!confirm('Supprimer la vidéo de ce cours ?')) return;
    this.videoDeleting = true;
    this.http.delete(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/video`,
      { headers: this.headers() }
    ).subscribe({
      next: () => {
        this.videoType = null;
        this.videoUrl  = null;
        this.videoDeleting = false;
        const c = this.cours.find((x:any) => x.id === this.currentCoursId);
        if (c) { c.videoType = null; c.videoUrl = null; }
        this.showToast('Vidéo supprimée.', 'success');
        this.cdr.detectChanges();
      },
      error: () => {
        this.videoDeleting = false;
        this.showToast('Erreur lors de la suppression.', 'error');
      }
    });
  }

  private updateCoursInList(updated: any) {
    const idx = this.cours.findIndex((c:any) => c.id === updated.id);
    if (idx !== -1) this.cours[idx] = { ...this.cours[idx], ...updated };
  }

  extractYoutubeId(url: string): string | null {
    if (!url) return null;
    const m = url.match(/(?:youtube\.com\/(?:watch\?v=|shorts\/|embed\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/);
    return m ? m[1] : null;
  }

  getYoutubeThumbnail(url: string): string {
    const id = this.extractYoutubeId(url);
    return id ? `https://img.youtube.com/vi/${id}/mqdefault.jpg` : '';
  }

  getVideoStreamUrl(): string {
    if (!this.videoType || !this.videoUrl || !this.currentCoursId) return '';
    if (this.videoType === 'YOUTUBE') return this.videoUrl;
    return `${this.api}/cours/${this.currentCoursId}/video/stream/${this.videoUrl}`;
  }

  onThumbError(event: Event) {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  formatVideoTime(seconds: number): string {
    if (!seconds) return '0:00';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}`;
    return `${m}:${s.toString().padStart(2,'0')}`;
  }

  formatFileSize(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }

  private showToast(msg: string, type: 'success' | 'error') {
    this.coursToast = msg;
    this.coursToastType = type;
    this.cdr.detectChanges();
    setTimeout(() => { this.coursToast = ''; this.cdr.detectChanges(); }, 3000);
  }

  // ══ DOCUMENTS ══════════════════════════════════════════════

  loadDocuments(): void {
    if (!this.currentCoursId || !this.selectedFormation) return;
    this.docLoading = true;
    this.http.get<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/documents`,
      { headers: this.headers() }
    ).subscribe({
      next: (res) => {
        this.documents = (res.documents || []).map((d: any) => ({
          ...d,
          _editing: false,
          _editTitre: d.titre
        }));
        this.docTotalTaille = res.totalTaille || 0;
        this.docLoading = false;
        const c = this.cours.find(x => x.id === this.currentCoursId);
        if (c) c.nbDocuments = this.documents.length;
        this.cdr.detectChanges();
      },
      error: () => {
        this.documents = [];
        this.docLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  onDocDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.docDragOver = true;
  }

  onDocDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.docDragOver = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.uploadDocFiles(Array.from(files));
    }
  }

  onDocFilesSelected(event: Event): void {
    const files = (event.target as HTMLInputElement).files;
    if (files && files.length > 0) {
      this.uploadDocFiles(Array.from(files));
      (event.target as HTMLInputElement).value = '';
    }
  }

  uploadDocFiles(files: File[]): void {
    const remaining = 10 - this.documents.length;
    const filesToUpload = files.slice(0, remaining);
    for (const file of filesToUpload) {
      this.uploadSingleDoc(file);
    }
  }

  uploadSingleDoc(file: File): void {
    if (!this.currentCoursId || !this.selectedFormation) return;

    const upEntry = { name: file.name, progress: 0 };
    this.docUploading.push(upEntry);
    this.cdr.detectChanges();

    const formData = new FormData();
    formData.append('fichier', file);

    const token = localStorage.getItem('formateur_token');
    const xhr = new XMLHttpRequest();

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        upEntry.progress = Math.round((e.loaded / e.total) * 100);
        this.cdr.detectChanges();
      }
    };

    xhr.onload = () => {
      const idx = this.docUploading.indexOf(upEntry);
      if (idx !== -1) this.docUploading.splice(idx, 1);

      if (xhr.status === 200) {
        const doc = JSON.parse(xhr.responseText);
        this.documents.push({ ...doc, _editing: false, _editTitre: doc.titre });
        this.docTotalTaille += doc.taille || 0;
        const c = this.cours.find((x: any) => x.id === this.currentCoursId);
        if (c) c.nbDocuments = (c.nbDocuments || 0) + 1;
        this.showToast(`"${doc.titre}" ajouté avec succès !`, 'success');
      } else {
        try {
          const err = JSON.parse(xhr.responseText);
          this.showToast(err.message || 'Erreur lors de l\'upload.', 'error');
        } catch {
          this.showToast('Erreur lors de l\'upload.', 'error');
        }
      }
      this.cdr.detectChanges();
    };

    xhr.onerror = () => {
      const idx = this.docUploading.indexOf(upEntry);
      if (idx !== -1) this.docUploading.splice(idx, 1);
      this.showToast(`Erreur réseau pour "${file.name}".`, 'error');
      this.cdr.detectChanges();
    };

    xhr.open('POST',
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/documents/upload`
    );
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    xhr.send(formData);
  }

  startEditDoc(doc: any): void {
    doc._editing = true;
    doc._editTitre = doc.titre;
    this.cdr.detectChanges();
    setTimeout(() => {
      const el = document.getElementById('doc-title-' + doc.id) as HTMLInputElement;
      if (el) { el.focus(); el.select(); }
    }, 50);
  }

  saveDocTitre(doc: any): void {
    const titre = doc._editTitre?.trim();
    if (!titre) return;
    this.http.put<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/documents/${doc.id}`,
      { titre },
      { headers: this.headers() }
    ).subscribe({
      next: (updated) => {
        doc.titre = updated.titre;
        doc._editing = false;
        this.showToast('Titre modifié avec succès !', 'success');
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.showToast(err.error?.message || 'Erreur lors de la modification.', 'error');
      }
    });
  }

  deleteDoc(doc: any): void {
    if (!confirm(`Supprimer le document "${doc.titre}" ?`)) return;
    this.http.delete(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/documents/${doc.id}`,
      { headers: this.headers() }
    ).subscribe({
      next: () => {
        this.documents = this.documents.filter(d => d.id !== doc.id);
        this.docTotalTaille = Math.max(0, this.docTotalTaille - (doc.taille || 0));
        const c = this.cours.find((x: any) => x.id === this.currentCoursId);
        if (c && c.nbDocuments > 0) c.nbDocuments--;
        this.showToast('Document supprimé.', 'success');
        this.cdr.detectChanges();
      },
      error: () => this.showToast('Erreur lors de la suppression.', 'error')
    });
  }

  downloadDoc(doc: any): void {
    if (!this.selectedFormation || !this.currentCoursId) return;
    this.http.get(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/documents/${doc.id}/download`,
      { headers: this.headers(), responseType: 'blob' }
    ).subscribe({
      next: blob => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.nomFichier || doc.titre;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.showToast('Erreur lors du téléchargement.', 'error')
    });
  }

  getDocIconClass(type: string): string {
    if (!type) return 'doc-icon-other';
    if (type.includes('pdf'))        return 'doc-icon-pdf';
    if (type.includes('word') || type.includes('msword')) return 'doc-icon-word';
    if (type.includes('powerpoint') || type.includes('presentation')) return 'doc-icon-ppt';
    if (type.includes('excel') || type.includes('sheet')) return 'doc-icon-excel';
    if (type.includes('image'))      return 'doc-icon-img';
    if (type.includes('text'))       return 'doc-icon-txt';
    return 'doc-icon-other';
  }

  getDocLabel(type: string): string {
    if (!type) return 'DOC';
    if (type.includes('pdf'))        return 'PDF';
    if (type.includes('word') || type.includes('msword')) return 'DOC';
    if (type.includes('powerpoint') || type.includes('presentation')) return 'PPT';
    if (type.includes('excel') || type.includes('sheet')) return 'XLS';
    if (type.includes('image'))      return 'IMG';
    if (type.includes('text'))       return 'TXT';
    return 'FILE';
  }

  // ── Charger profil ─────────────────────────────────────────
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
        error: err => {
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
      ...this.profilForm.value,
      photo: this.photoPreview,
      competences:    this.competences,
      reseauxSociaux: this.reseauxSociaux,
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
    this.profilSuccess = ''; this.profilError = '';
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

  // ══ MINI-QUIZ ══════════════════════════════════════════════

  onMiniQuizTabClick() {
    if (this.coursModalMode !== 'create') {
      this.coursModalTab = 'mini-quiz';
      this.mqEditingId = null;
      this.mqEditForm = null;
      this.mqEditError = '';

      // ── CORRECTION : ne pas remettre à null si le quiz est déjà chargé
      //    On recharge uniquement si miniQuiz est null (première ouverture)
      if (!this.miniQuiz) {
        this.miniQuizLoading = true;
        this.loadMiniQuiz();
      }
    }
  }

  loadMiniQuiz() {
    if (!this.currentCoursId || !this.selectedFormation) return;
    this.miniQuizLoading = true;
    this.http.get<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/mini-quiz`,
      { headers: this.headers() }
    ).subscribe({
      next: res => {
        if (res && res.exists === true) {
          this.miniQuiz = res;
          this.miniQuizExists = true;
          // Mettre à jour le badge dans la liste
          const c = this.cours.find((x: any) => x.id === this.currentCoursId);
          if (c) c.hasQuiz = true;
        } else {
          this.miniQuiz = null;
          this.miniQuizExists = false;
          this.loadMiniQuizContexte();
        }
        this.miniQuizLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.miniQuiz = null;
        this.miniQuizExists = false;
        this.miniQuizLoading = false;
        this.loadMiniQuizContexte();
        this.cdr.detectChanges();
      }
    });
  }

  loadMiniQuizContexte() {
    if (!this.currentCoursId || !this.selectedFormation) return;
    this.http.get<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/mini-quiz/contexte`,
      { headers: this.headers() }
    ).subscribe({
      next: ctx => { this.miniQuizContexte = ctx; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  openGenerateMiniQuiz() {
    this.miniQuizContexte = null;
    this.loadMiniQuizContexte();
    this.showMiniQuizModal = true;
    this.miniQuizParams = {
      nombreQuestions: 5,
      difficulte: 'MOYEN',
      inclureDefinitions: true,
      inclureCasPratiques: true
    };
    this.cdr.detectChanges();
  }

  generateMiniQuiz() {
    if (!this.currentCoursId || !this.selectedFormation) return;
    this.miniQuizGenerating = true;
    this.cdr.detectChanges();

    this.http.post<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/mini-quiz/generer-ia`,
      this.miniQuizParams,
      { headers: this.headers() }
    ).subscribe({
      next: quiz => {
        this.miniQuizGenerating = false;
        this.showMiniQuizModal  = false;
        this.mqEditingId        = null;
        this.mqEditForm         = null;
        this.miniQuiz           = quiz;
        this.miniQuizExists     = true;
        this.miniQuizContexte   = null;
        // ── NOUVEAU : mettre à jour le badge dans la liste des cours
        const c = this.cours.find((x: any) => x.id === this.currentCoursId);
        if (c) c.hasQuiz = true;
        this.showToast('Mini-quiz généré avec succès par l\'IA !', 'success');
        this.cdr.detectChanges();
      },
      error: err => {
        this.miniQuizGenerating = false;
        this.showToast(err.error?.message || 'Erreur lors de la génération.', 'error');
        this.cdr.detectChanges();
      }
    });
  }

  deleteMiniQuiz() {
    if (!confirm('Supprimer le mini-quiz de ce cours ?')) return;
    this.http.delete(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/mini-quiz`,
      { headers: this.headers() }
    ).subscribe({
      next: () => {
        this.miniQuiz = null;
        this.miniQuizExists = false;
        this.mqEditingId = null;
        this.mqEditForm = null;
        // ── NOUVEAU : retirer le badge dans la liste des cours
        const c = this.cours.find((x: any) => x.id === this.currentCoursId);
        if (c) c.hasQuiz = false;
        this.loadMiniQuizContexte();
        this.showToast('Mini-quiz supprimé.', 'success');
        this.cdr.detectChanges();
      },
      error: () => this.showToast('Erreur lors de la suppression.', 'error')
    });
  }

  regenerateMiniQuiz() {
    this.showMiniQuizModal = true;
    this.miniQuizParams = {
      nombreQuestions:     this.miniQuiz?.nombreQuestions  || 5,
      difficulte:          this.miniQuiz?.niveauDifficulte || this.miniQuiz?.difficulte || 'MOYEN',
      inclureDefinitions:  this.miniQuiz?.inclureDefinitions  ?? true,
      inclureCasPratiques: this.miniQuiz?.inclureCasPratiques ?? true
    };
    this.cdr.detectChanges();
  }

  quickRegenerateWithDiff(difficulte: string) {
    if (!this.currentCoursId || !this.selectedFormation) return;
    this.miniQuizParams = {
      nombreQuestions:     this.miniQuiz?.nombreQuestions  || 5,
      difficulte:          difficulte,
      inclureDefinitions:  this.miniQuiz?.inclureDefinitions  ?? true,
      inclureCasPratiques: this.miniQuiz?.inclureCasPratiques ?? true
    };
    this.generateMiniQuiz();
  }

  getMiniQuizDifficulteLabel(d: string): string {
    const m: any = { FACILE: 'Facile', MOYEN: 'Moyen', DIFFICILE: 'Difficile' };
    return m[d] || d || 'Moyen';
  }

  getMiniQuizDifficulteColor(d: string): string {
    if (d === 'FACILE')    return '#27ae60';
    if (d === 'DIFFICILE') return '#8B3A3A';
    return '#e67e22';
  }

  // ── US-019 : Édition inline ────────────────────────────────

  startEditQuestion019(q: any) {
    this.mqEditingId  = q.id;
    this.mqEditError  = '';
    this.mqEditForm = {
      id:          q.id,
      texte:       q.texte       || '',
      explication: q.explication || '',
      options:     (q.options || []).map((o: any) => ({ ...o }))
    };
    this.cdr.detectChanges();
  }

  cancelEditQuestion019() {
    this.mqEditingId = null;
    this.mqEditForm  = null;
    this.mqEditError = '';
    this.cdr.detectChanges();
  }

  setCorrectOption019(index: number) {
    if (!this.mqEditForm?.options) return;
    this.mqEditForm.options.forEach((o: any, i: number) => { o.estCorrecte = (i === index); });
    this.cdr.detectChanges();
  }

  saveEditQuestion019() {
    if (!this.mqEditForm || !this.currentCoursId || !this.selectedFormation) return;
    if (!this.mqEditForm.texte?.trim()) { this.mqEditError = 'Le texte de la question est obligatoire.'; return; }
    for (const opt of this.mqEditForm.options) {
      if (!opt.texte?.trim()) { this.mqEditError = 'Toutes les options doivent avoir un texte.'; return; }
    }
    const nbCorrects = this.mqEditForm.options.filter((o: any) => o.estCorrecte).length;
    if (nbCorrects !== 1) { this.mqEditError = 'Exactement 1 option doit être la bonne réponse.'; return; }

    this.mqSaving    = true;
    this.mqEditError = '';
    this.cdr.detectChanges();

    const qId     = this.mqEditForm.id;
    const formId  = this.selectedFormation.id;
    const coursId = this.currentCoursId;
    const baseUrl = `${this.api}/formations/${formId}/cours/${coursId}/mini-quiz`;

    this.http.put<any>(
      `${baseUrl}/questions/${qId}`,
      { texte: this.mqEditForm.texte.trim(), explication: this.mqEditForm.explication?.trim() || '' },
      { headers: this.headers() }
    ).subscribe({
      next: (updatedQuiz) => {
        const optionUpdates = this.mqEditForm.options.map((opt: any) =>
          this.http.patch<any>(
            `${baseUrl}/questions/${qId}/options/${opt.id}`,
            { texte: opt.texte.trim() },
            { headers: this.headers() }
          ).toPromise().catch(() => null)
        );
        Promise.all(optionUpdates).then(() => {
          const correctOpt = this.mqEditForm.options.find((o: any) => o.estCorrecte);
          this.http.patch<any>(
            `${baseUrl}/questions/${qId}/bonne-reponse/${correctOpt.id}`,
            {},
            { headers: this.headers() }
          ).subscribe({
            next: (finalQuiz) => {
              this.miniQuiz    = finalQuiz;
              this.mqEditingId = null;
              this.mqEditForm  = null;
              this.mqSaving    = false;
              this.showToast('Question mise à jour avec succès !', 'success');
              this.cdr.detectChanges();
            },
            error: () => {
              this.miniQuiz    = updatedQuiz;
              this.mqEditingId = null;
              this.mqEditForm  = null;
              this.mqSaving    = false;
              this.showToast('Modifications partiellement enregistrées.', 'success');
              this.cdr.detectChanges();
            }
          });
        });
      },
      error: (err) => {
        this.mqSaving    = false;
        this.mqEditError = err.error?.message || 'Erreur lors de la sauvegarde.';
        this.cdr.detectChanges();
      }
    });
  }

  // ── US-019 : Suppression avec confirmation ─────────────────

  confirmDeleteQuestion019(q: any, index: number) {
    this.mqDeleteQuestion      = q;
    this.mqDeleteQuestionIndex = index;
    this.showDeleteQuestionModal = true;
    this.cdr.detectChanges();
  }

  deleteQuestion019() {
    if (!this.mqDeleteQuestion || !this.currentCoursId || !this.selectedFormation) return;
    this.mqDeleteSaving = true;
    this.cdr.detectChanges();
    const qId = this.mqDeleteQuestion.id;
    this.http.delete<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/mini-quiz/questions/${qId}`,
      { headers: this.headers() }
    ).subscribe({
      next: () => {
        if (this.miniQuiz?.questions) {
          this.miniQuiz.questions = this.miniQuiz.questions.filter((q: any) => q.id !== qId);
          this.miniQuiz.questions.forEach((q: any, i: number) => q.ordre = i + 1);
          this.miniQuiz.nbQuestions = this.miniQuiz.questions.length;
        }
        this.showDeleteQuestionModal = false;
        this.mqDeleteSaving          = false;
        this.mqDeleteQuestion        = null;
        if (this.miniQuiz?.questions?.length === 0) {
          this.miniQuizExists = false;
          this.miniQuiz       = null;
          const c = this.cours.find((x: any) => x.id === this.currentCoursId);
          if (c) c.hasQuiz = false;
        }
        this.showToast('Question supprimée.', 'success');
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.mqDeleteSaving = false;
        this.showToast(err.error?.message || 'Erreur lors de la suppression.', 'error');
        this.cdr.detectChanges();
      }
    });
  }

  // ── US-019 : Ajout manuel ──────────────────────────────────

  openAddQuestionModal() {
    this.mqNewQuestion = {
      texte: '', explication: '',
      options: [
        { ordre: 'A', texte: '', estCorrecte: true  },
        { ordre: 'B', texte: '', estCorrecte: false },
        { ordre: 'C', texte: '', estCorrecte: false },
        { ordre: 'D', texte: '', estCorrecte: false },
      ]
    };
    this.mqAddError           = '';
    this.showAddQuestionModal = true;
    this.cdr.detectChanges();
  }

  setNewCorrectOption(index: number) {
    this.mqNewQuestion.options.forEach((o: any, i: number) => { o.estCorrecte = (i === index); });
    this.cdr.detectChanges();
  }

  submitAddQuestion() {
    if (!this.currentCoursId || !this.selectedFormation) return;
    if (!this.mqNewQuestion.texte?.trim()) { this.mqAddError = 'Le texte de la question est obligatoire.'; return; }
    for (const opt of this.mqNewQuestion.options) {
      if (!opt.texte?.trim()) { this.mqAddError = 'Toutes les options doivent avoir un texte.'; return; }
    }
    const nbCorrects = this.mqNewQuestion.options.filter((o: any) => o.estCorrecte).length;
    if (nbCorrects !== 1) { this.mqAddError = 'Sélectionnez exactement 1 bonne réponse.'; return; }

    this.mqAddSaving = true;
    this.mqAddError  = '';
    this.cdr.detectChanges();

    const payload = {
      texte:       this.mqNewQuestion.texte.trim(),
      explication: this.mqNewQuestion.explication?.trim() || '',
      options:     this.mqNewQuestion.options.map((o: any) => ({
        ordre: o.ordre, texte: o.texte.trim(), estCorrecte: o.estCorrecte
      }))
    };

    this.http.post<any>(
      `${this.api}/formations/${this.selectedFormation.id}/cours/${this.currentCoursId}/mini-quiz/questions`,
      payload,
      { headers: this.headers() }
    ).subscribe({
      next: (updatedQuiz) => {
        this.miniQuiz             = updatedQuiz;
        this.miniQuizExists       = true;
        this.mqAddSaving          = false;
        this.showAddQuestionModal = false;
        // Mettre à jour le badge
        const c = this.cours.find((x: any) => x.id === this.currentCoursId);
        if (c) c.hasQuiz = true;
        this.showToast('Question ajoutée avec succès !', 'success');
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.mqAddSaving = false;
        this.mqAddError  = err.error?.message || 'Erreur lors de l\'ajout.';
        this.cdr.detectChanges();
      }
    });
  }

  // ── Aliases sans suffixe (appelés depuis le HTML) ────────
  startEditQuestion(q: any)                { this.startEditQuestion019(q); }
  cancelEditQuestion()                     { this.cancelEditQuestion019(); }
  saveEditQuestion()                       { this.saveEditQuestion019(); }
  setCorrectOption(index: number)          { this.setCorrectOption019(index); }
  confirmDeleteQuestion(q: any, i: number) { this.confirmDeleteQuestion019(q, i); }
  deleteQuestion()                         { this.deleteQuestion019(); }

  logout() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
    localStorage.clear();
    this.router.navigate(['/login']);
  }
}