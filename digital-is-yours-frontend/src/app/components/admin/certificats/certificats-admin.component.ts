import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-certificats-admin',
  templateUrl: './certificats-admin.component.html',
  styleUrls: ['./certificats-admin.component.css']
})
export class CertificatsAdminComponent implements OnInit {

  private api = 'http://localhost:8080/api/admin/certificats';

  // ── Stats ──
  stats = { totalCeMois: 0, formationsActives: 0, tauxReussite: 0, total: 0 };

  // ── Liste ──
  certificats: any[] = [];
  isLoading = false;

  // ── Filtres ──
  filterFormation = '';
  filterApprenant = '';
  filterDateDebut = '';
  filterDateFin   = '';
  search          = '';

  // ── Pagination ──
  page       = 0;
  size       = 10;
  total      = 0;
  totalPages = 0;

  // ── Toast ──
  toast: { msg: string; type: 'success' | 'error' } | null = null;

  // ── Sending ──
  sendingId: number | null = null;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.loadStats();
    this.loadCertificats();
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => { this.toast = null; this.cdr.detectChanges(); }, 4000);
  }

  // ── Chargement stats ─────────────────────────────────────
  loadStats() {
    this.http.get<any>(`${this.api}/stats`, { headers: this.headers() })
      .subscribe({
        next: d => { this.stats = d; this.cdr.detectChanges(); },
        error: () => {}
      });
  }

  // ── Chargement liste ─────────────────────────────────────
  loadCertificats(resetPage = false) {
    if (resetPage) this.page = 0;
    this.isLoading = true;

    const params: any = { page: this.page, size: this.size };
    if (this.filterFormation) params['formation'] = this.filterFormation;
    if (this.filterApprenant) params['apprenant'] = this.filterApprenant;
    if (this.filterDateDebut) params['dateDebut']  = this.filterDateDebut;
    if (this.filterDateFin)   params['dateFin']    = this.filterDateFin;
    if (this.search)          params['search']     = this.search;

    this.http.get<any>(this.api, { headers: this.headers(), params })
      .subscribe({
        next: d => {
          this.certificats = d.certificats  || [];
          this.total       = d.total        || 0;
          this.totalPages  = d.totalPages   || 0;
          this.isLoading   = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.isLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

  // ── Actions filtres ───────────────────────────────────────
  onSearch()        { this.loadCertificats(true); }
  onFilterChange()  { this.loadCertificats(true); }
  resetFiltres() {
    this.filterFormation = '';
    this.filterApprenant = '';
    this.filterDateDebut = '';
    this.filterDateFin   = '';
    this.search          = '';
    this.loadCertificats(true);
  }

  // ── Pagination ────────────────────────────────────────────
  goToPage(p: number) {
    if (p < 0 || p >= this.totalPages) return;
    this.page = p;
    this.loadCertificats();
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  // ── Envoi email ───────────────────────────────────────────
  envoyerEmail(cert: any) {
    if (this.sendingId === cert.id) return;
    this.sendingId = cert.id;
    this.cdr.detectChanges();

    this.http.post<any>(`${this.api}/${cert.id}/envoyer-email`, {}, { headers: this.headers() })
      .subscribe({
        next: r => {
          this.sendingId = null;
          cert.estEnvoye = true;
          this.showToast(r.message || 'Email envoyé avec succès !');
          this.cdr.detectChanges();
        },
        error: e => {
          this.sendingId = null;
          this.showToast(e.error?.message || 'Erreur lors de l\'envoi.', 'error');
          this.cdr.detectChanges();
        }
      });
  }

  // ── Export CSV ────────────────────────────────────────────
  exportCSV() {
    const params: any = {};
    if (this.filterFormation) params['formation'] = this.filterFormation;
    if (this.filterApprenant) params['apprenant'] = this.filterApprenant;
    if (this.filterDateDebut) params['dateDebut']  = this.filterDateDebut;
    if (this.filterDateFin)   params['dateFin']    = this.filterDateFin;
    if (this.search)          params['search']     = this.search;

    const qStr = Object.entries(params).map(([k, v]) => `${k}=${v}`).join('&');
    const url  = `${this.api}/export${qStr ? '?' + qStr : ''}`;

    const token = localStorage.getItem('admin_token');
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.blob())
      .then(blob => {
        const a = document.createElement('a');
        a.href     = URL.createObjectURL(blob);
        a.download = `certificats_${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(a.href);
        this.showToast('Export CSV téléchargé !');
      })
      .catch(() => this.showToast('Erreur export CSV.', 'error'));
  }

  // ── Copier lien ───────────────────────────────────────────
  copierLien(cert: any) {
    const url = `http://localhost:8080/api/apprenant/certificats/${cert.id}/download`;
    navigator.clipboard.writeText(url).then(() => {
      this.showToast('Lien copié dans le presse-papiers !');
    });
  }

  // ── Télécharger PDF ───────────────────────────────────────
  downloadPdf(cert: any) {
    const token = localStorage.getItem('admin_token');
    fetch(`http://localhost:8080/api/apprenant/certificats/${cert.id}/download`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    .then(r => r.blob())
    .then(blob => {
      const a = document.createElement('a');
      a.href     = URL.createObjectURL(blob);
      const num  = (cert.numeroCertificat || 'cert').replace('#', '').replace(/-/g, '_');
      a.download = `certificat_${num}.pdf`;
      a.click();
      URL.revokeObjectURL(a.href);
    })
    .catch(() => this.showToast('Erreur téléchargement PDF.', 'error'));
  }

  // ── Helpers ───────────────────────────────────────────────
  getInitiales(cert: any): string {
    const p = cert.apprenantPrenom?.[0] || '';
    const n = cert.apprenantNom?.[0]    || '';
    return (p + n).toUpperCase() || '?';
  }

  getScoreColor(note: number): string {
    if (note >= 90) return '#27ae60';
    if (note >= 75) return '#4A7C7E';
    if (note >= 60) return '#e67e22';
    return '#c0392b';
  }

  getScoreBg(note: number): string {
    if (note >= 90) return 'rgba(39,174,96,.12)';
    if (note >= 75) return 'rgba(74,124,126,.12)';
    if (note >= 60) return 'rgba(230,126,34,.12)';
    return 'rgba(192,57,43,.12)';
  }

  getMention(note: number): string {
    if (note >= 90) return 'Très Bien';
    if (note >= 75) return 'Bien';
    if (note >= 60) return 'Assez Bien';
    return 'Admis';
  }

  formatDate(d: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  getNiveauLabel(n: string): string {
    const m: any = { DEBUTANT: 'Débutant', INTERMEDIAIRE: 'Intermédiaire', AVANCE: 'Avancé' };
    return m[n] || n || '—';
  }
}
