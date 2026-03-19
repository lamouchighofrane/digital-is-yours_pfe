import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-cours-detail',
  templateUrl: './cours-detail.component.html',
  styleUrls: ['./cours-detail.component.css']
})
export class CoursDetailComponent implements OnInit {

  cours: any = null;
  formation: any = null;
  tousLesCours: any[] = [];
  documents: any[] = [];
  documentsLoading = false;
  loading = true;
  error = '';

  formationId!: number;
  coursId!: number;

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

  private headers() {
    return new HttpHeaders({ 
      Authorization: `Bearer ${localStorage.getItem('token')}` 
    });
  }

  loadCours() {
    this.loading = true;
    this.error   = '';

    // Charger tous les cours de la formation
    this.http.get<any>(
      `${this.api}/formations/${this.formationId}/cours`,
      { headers: this.headers() }
    ).subscribe({
      next: res => {
  this.tousLesCours = res.cours || [];
  this.cours = this.tousLesCours.find(c => c.id === this.coursId);
  
  // Récupérer le titre de la formation depuis le query param
  this.formation = { 
    titre: this.route.snapshot.queryParamMap.get('titre') || 'Formation' 
  };
  
  if (!this.cours) {
    this.error = 'Cours introuvable.';
    this.loading = false;
    return;
  }
  this.loading = false;
  this.loadDocuments();
},
      error: err => {
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
      next: res => {
        this.documents        = res.documents || [];
        this.documentsLoading = false;
      },
      error: () => {
        this.documents        = [];
        this.documentsLoading = false;
      }
    });
  }

retour() {
  this.router.navigate(['/apprenant/dashboard'], {
    queryParams: { 
      formationId: this.formationId 
    }
  });
}

  allerAuCours(c: any, i: number) {
    // Vérifier si verrouillé
    if (i > 0 && !this.tousLesCours[i - 1].estTermine && !c.estTermine) return;
    
    // Naviguer vers le nouveau cours
    this.router.navigate(['/apprenant/cours', this.formationId, c.id]);
  }

  getYoutubeEmbedUrl(url: string): SafeResourceUrl {
    if (!url) return '';
    const m = url.match(
      /(?:youtube\.com\/(?:watch\?v=|shorts\/|embed\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/
    );
    const embedUrl = m 
      ? `https://www.youtube.com/embed/${m[1]}?autoplay=0&rel=0` 
      : '';
    return this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl);
  }

  getVideoUrl(): SafeResourceUrl {
    if (!this.cours) return '';
    const url = `http://localhost:8080/api/apprenant/cours/${this.coursId}/video/stream/${this.cours.videoUrl}?formationId=${this.formationId}`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  downloadDocument(doc: any) {
    const token = localStorage.getItem('token');
    fetch(
      `${this.api}/formations/${this.formationId}/cours/${this.coursId}/documents/${doc.id}/download`,
      { headers: { Authorization: `Bearer ${token}` } }
    ).then(res => res.blob()).then(blob => {
      const url = window.URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href     = url;
      a.download = doc.nomFichier || doc.titre;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  getDocIconLabel(type: string): string {
    if (!type) return 'FILE';
    if (type.includes('pdf'))         return 'PDF';
    if (type.includes('word'))        return 'DOC';
    if (type.includes('powerpoint'))  return 'PPT';
    if (type.includes('excel'))       return 'XLS';
    if (type.includes('image'))       return 'IMG';
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