import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-forum-admin',
  templateUrl: './forum-admin.component.html',
  styleUrls: ['./forum-admin.component.css']
})
export class ForumAdminComponent implements OnInit {

  questions: any[] = [];
  isLoading = false;
  searchTerm = '';
  filterStatut = 'ALL';

  page = 0;
  totalPages = 0;
  total = 0;

  stats: any = {
    totalQuestions: 0, nonRepondues: 0,
    repondues: 0, resolues: 0, totalReponses: 0
  };

  // Vue détail
  selectedQuestion: any = null;
  detailLoading = false;

  toast: { msg: string; type: 'success' | 'error' } | null = null;

  private api = 'http://localhost:8080/api/admin/forum';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadStats();
    this.loadQuestions();
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => this.toast = null, 4000);
  }

  loadStats() {
    this.http.get<any>(`${this.api}/stats`, { headers: this.headers() })
      .subscribe({ next: d => this.stats = d, error: () => {} });
  }

  loadQuestions(p = 0) {
    this.isLoading = true;
    const params = new URLSearchParams({
      page:   String(p),
      size:   '10',
      search: this.searchTerm,
      statut: this.filterStatut === 'ALL' ? '' : this.filterStatut
    });
    this.http.get<any>(`${this.api}/questions?${params}`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.questions   = d.questions   || [];
          this.total       = d.total       || 0;
          this.totalPages  = d.totalPages  || 0;
          this.page        = d.currentPage || 0;
          this.isLoading   = false;
        },
        error: () => { this.isLoading = false; }
      });
  }

  onSearch() { this.loadQuestions(0); }

  changerPage(p: number) {
    if (p < 0 || p >= this.totalPages) return;
    this.loadQuestions(p);
  }

  ouvrirDetail(q: any) {
    this.detailLoading = true;
    this.selectedQuestion = null;
    this.http.get<any>(`${this.api}/questions/${q.id}`, { headers: this.headers() })
      .subscribe({
        next: d => { this.selectedQuestion = d; this.detailLoading = false; },
        error: () => { this.detailLoading = false; }
      });
  }

  fermerDetail() { this.selectedQuestion = null; }

  supprimerQuestion(q: any, event?: Event) {
    event?.stopPropagation();
    if (!confirm(`Supprimer la question "${q.titre}" et toutes ses réponses ?`)) return;
    this.http.delete<any>(`${this.api}/questions/${q.id}`, { headers: this.headers() })
      .subscribe({
        next: () => {
          this.questions = this.questions.filter(x => x.id !== q.id);
          this.loadStats();
          if (this.selectedQuestion?.id === q.id) this.selectedQuestion = null;
          this.showToast('Question supprimée');
        },
        error: e => this.showToast(e.error?.message || 'Erreur', 'error')
      });
  }

  supprimerReponse(r: any) {
    if (!confirm('Supprimer cette réponse ?')) return;
    this.http.delete<any>(`${this.api}/reponses/${r.id}`, { headers: this.headers() })
      .subscribe({
        next: () => {
          if (this.selectedQuestion?.reponses) {
            this.selectedQuestion.reponses = this.selectedQuestion.reponses.filter((x: any) => x.id !== r.id);
            this.selectedQuestion.nombreReponses = this.selectedQuestion.reponses.length;
          }
          this.loadStats();
          this.showToast('Réponse supprimée');
        },
        error: e => this.showToast(e.error?.message || 'Erreur', 'error')
      });
  }

  getStatutBg(s: string) {
    return s === 'REPONDU' ? 'rgba(39,174,96,.15)'
         : s === 'RESOLU'  ? 'rgba(74,124,126,.15)'
         : 'rgba(243,156,18,.15)';
  }
  getStatutColor(s: string) {
    return s === 'REPONDU' ? '#27ae60'
         : s === 'RESOLU'  ? '#4A7C7E'
         : '#f39c12';
  }
  getStatutLabel(s: string) {
    return s === 'REPONDU' ? '✓ Répondu'
         : s === 'RESOLU'  ? '⭐ Résolu'
         : '● En attente';
  }
  formatDate(dateStr: string): string {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}
downloadReponseDoc(reponseId: number, doc: any) {
  const token = localStorage.getItem('admin_token');
  fetch(
    `http://localhost:8080/api/formateur/forum/reponses/${reponseId}/documents/${doc.url}/download`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
    .then(r => r.blob())
    .then(blob => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.nomFichier;
      a.click();
      window.URL.revokeObjectURL(url);
    })
    .catch(() => this.showToast('Erreur lors du téléchargement.', 'error'));
}
}
