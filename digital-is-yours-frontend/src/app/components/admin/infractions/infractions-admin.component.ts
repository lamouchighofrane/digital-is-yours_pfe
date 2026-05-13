import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-infractions-admin',
  templateUrl: './infractions-admin.component.html',
  styleUrls: ['./infractions-admin.component.css']
})
export class InfractionsAdminComponent implements OnInit {

  infractions: any[] = [];
  isLoading = false;
  searchTerm = '';
  filterType = 'ALL';

  page = 0;
  totalPages = 0;
  total = 0;

  stats: any = {
    totalSuspects: 0, totalInfractions: 0,
    totalMiniSuspects: 0, totalFinalSuspects: 0,
    totalMiniInfractions: 0, totalFinalInfractions: 0
  };

  selectedInfraction: any = null;
  infractionsDetail: any[] = [];

  toast: { msg: string; type: 'success' | 'error' } | null = null;

  private api = 'http://localhost:8080/api/admin';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadStats();
    this.loadInfractions();
  }

  private headers() {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => this.toast = null, 4000);
  }

  loadStats() {
    this.http.get<any>(`${this.api}/infractions/stats`, { headers: this.headers() })
      .subscribe({ next: d => this.stats = d, error: () => {} });
  }

  loadInfractions(p = 0) {
    this.isLoading = true;
    const params = new URLSearchParams({
      page:   String(p),
      size:   '10',
      search: this.searchTerm,
      type:   this.filterType
    });
    this.http.get<any>(`${this.api}/infractions?${params}`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.infractions  = d.infractions  || [];
          this.total        = d.total        || 0;
          this.totalPages   = d.totalPages   || 0;
          this.page         = d.currentPage  || 0;
          this.isLoading    = false;
        },
        error: () => { this.isLoading = false; }
      });
  }

  onSearch() { this.loadInfractions(0); }

  changerPage(p: number) {
    if (p < 0 || p >= this.totalPages) return;
    this.loadInfractions(p);
  }

  ouvrirDetail(item: any) {
    this.selectedInfraction = item;
    this.infractionsDetail = [];
    if (item.detailInfractions) {
      try {
        this.infractionsDetail = JSON.parse(item.detailInfractions);
      } catch { this.infractionsDetail = []; }
    }
  }

  fermerDetail() {
    this.selectedInfraction = null;
    this.infractionsDetail  = [];
  }

  getTypeColor(t: string): string {
    const map: any = {
      'onglet_quitte':   '#f39c12',
      'copie':           '#e74c3c',
      'raccourci':       '#8B3A3A',
      'plein_ecran':     '#9B8B6E',
      'absence_visage':  '#8B5CF6',
      'visages_multiples':'#e74c3c',
      'camera_refusee':  '#6B5F52'
    };
    return map[t] || '#6B5F52';
  }

  getTypeLabel(t: string): string {
    const map: any = {
      'onglet_quitte':    '🔀 Onglet quitté',
      'copie':            '📋 Tentative copie',
      'raccourci':        '⌨️ Raccourci suspect',
      'plein_ecran':      '⛶ Plein écran quitté',
      'absence_visage':   '👤 Absence visage',
      'visages_multiples':'👥 Visages multiples',
      'camera_refusee':   '📷 Caméra refusée'
    };
    return map[t] || t;
  }

  getNiveauRisque(nb: number): string {
    if (nb >= 3) return 'ELEVE';
    if (nb >= 1) return 'MOYEN';
    return 'FAIBLE';
  }

  getNiveauClass(nb: number): string {
    if (nb >= 3) return 'niveau-eleve';
    if (nb >= 1) return 'niveau-moyen';
    return 'niveau-faible';
  }

  formatDate(d: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getInitials(item: any): string {
    return ((item?.apprenantPrenom?.[0] || '') + (item?.apprenantNom?.[0] || '')).toUpperCase() || '?';
  }
}
