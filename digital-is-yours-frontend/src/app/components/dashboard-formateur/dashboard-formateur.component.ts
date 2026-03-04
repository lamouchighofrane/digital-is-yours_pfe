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
  coursModalTab: 'infos' | 'videos' = 'infos';

  // ── Vidéo — un seul champ videoType + videoUrl par cours ──
  // (selon diagramme : Cours a videoType: Enum(Local,YouTube) et videoUrl: String)
  videoType: string | null = null;      // 'LOCAL' | 'YOUTUBE' | null
  videoUrl: string | null = null;       // URL YouTube ou nom fichier local
  videoUploading = false;
  videoUploadProgress = 0;
  videoUploadingName = '';
  videoDragOver = false;
  youtubeUrlInput = '';
  videoYoutubeAdding = false;
  videoDeleting = false;
  private uploadXhr: XMLHttpRequest | null = null;
  private currentCoursId: number | null = null;

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
      // Depuis la sidebar : reset la formation pour afficher l'écran de choix
      this.selectedFormation = null;
      this.cours = [];
      this.coursStats = { total: 0, publies: 0, brouillons: 0 };
      this.loadFormations();
    }
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
        },
        error: () => { this.cours = []; this.coursLoading = false; this.cdr.detectChanges(); }
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
      // Charger l'état vidéo depuis le cours existant
      this.videoType = cours.videoType || null;
      this.videoUrl  = cours.videoUrl  || null;
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
    }
    this.showCoursModal = true;
    this.cdr.detectChanges();
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

  formatDuree(minutes: number): string {
    if (!minutes) return '';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0 && m > 0) return `${h}h ${m}min`;
    if (h > 0) return `${h}h`;
    return `${m}min`;
  }

  // ══ VIDÉO ═══════════════════════════════════════════════════
  // Selon diagramme : Cours.videoType (Enum Local/YouTube) + Cours.videoUrl
  // Un cours possède UNE vidéo (ou aucune)

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
        // Mettre à jour aussi dans la liste cours
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
        // Mettre à jour dans la liste
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

  // ── Helpers vidéo ──────────────────────────────────────────

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