import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-dashboard-formateur',
  templateUrl: './dashboard-formateur.component.html',
  styleUrls: ['./dashboard-formateur.component.css']
})
export class DashboardFormateurComponent implements OnInit, OnDestroy {

  activeSection: 'dashboard' | 'mes-formations' = 'dashboard';
  formateurUser: any = null;
  formations: any[] = [];
  stats: any = {
    totalApprenants: 0,
    tauxReussite: 0,
    nouveauxInscrits: 0,
    noteMoyenne: 0
  };
  activites: any[] = [];
  alertes: any[] = [];

  // ── Notifications ──────────────────────────────────────────
  notifications: any[] = [];
  notifNonLues: number = 0;
  showNotifPanel: boolean = false;
  notifLoading: boolean = false;

  private api = 'http://localhost:8080/api/formateur';
  private pollingInterval: any = null;

  constructor(
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('formateur_token');
    if (!token) { this.router.navigate(['/login']); return; }
    this.formateurUser = JSON.parse(localStorage.getItem('formateur_user') || '{}');
    this.loadDashboardData();
    this.loadNotifications();
    this.pollingInterval = setInterval(() => this.pollNotifCount(), 30000);
  }

  ngOnDestroy() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
  }

  private headers() {
    return new HttpHeaders({
      Authorization: `Bearer ${localStorage.getItem('formateur_token')}`
    });
  }

  setSection(section: 'dashboard' | 'mes-formations') {
    this.activeSection = section;
    if (section === 'mes-formations') this.loadFormations();
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

  // ══ NOTIFICATIONS ══════════════════════════════════════════

  loadNotifications() {
    this.notifLoading = true;
    this.http.get<any[]>(`${this.api}/notifications`, { headers: this.headers() })
      .subscribe({
        next: d => {
          this.notifications = d || [];
          this.notifNonLues = this.notifications.filter(n => !n.lu).length;
          this.notifLoading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.notifications = [];
          this.notifNonLues = 0;
          this.notifLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

  pollNotifCount() {
    this.http.get<any>(`${this.api}/notifications/count`, { headers: this.headers() })
      .subscribe({
        next: d => {
          const newCount = d.count || 0;
          if (newCount > this.notifNonLues) {
            this.loadNotifications();
          } else {
            this.notifNonLues = newCount;
            this.cdr.detectChanges();
          }
        },
        error: () => {}
      });
  }

  toggleNotifPanel(event: Event) {
    event.stopPropagation();
    this.showNotifPanel = !this.showNotifPanel;
    if (this.showNotifPanel && this.notifications.length === 0) {
      this.loadNotifications();
    }
    this.cdr.detectChanges();
  }

  closeNotifPanel() {
    this.showNotifPanel = false;
    this.cdr.detectChanges();
  }

  marquerLue(notif: any) {
    if (notif.lu) return;
    this.http.patch(`${this.api}/notifications/${notif.id}/lire`, {}, { headers: this.headers() })
      .subscribe({
        next: () => {
          notif.lu = true;
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
          this.notifNonLues = 0;
          this.showNotifPanel = false;
          this.cdr.detectChanges();
        },
        error: () => {}
      });
  }

  getNotifColor(type: string): string {
    if (type === 'FORMATION_AFFECTEE') return '#4A7C7E';
    if (type === 'FORMATION_RETIREE')  return '#8B3A3A';
    return '#9B8B6E';
  }

  getNotifBg(type: string): string {
    if (type === 'FORMATION_AFFECTEE') return 'rgba(74,124,126,.12)';
    if (type === 'FORMATION_RETIREE')  return 'rgba(139,58,58,.10)';
    return 'rgba(155,139,110,.10)';
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

  getFormateurInitiales(): string {
    const u = this.formateurUser;
    return ((u?.prenom?.[0] || '') + (u?.nom?.[0] || '')).toUpperCase() || 'F';
  }

  getNiveauLabel(niveau: string): string {
    const map: any = { DEBUTANT: 'Débutant', INTERMEDIAIRE: 'Intermédiaire', AVANCE: 'Avancé' };
    return map[niveau] || niveau || '—';
  }

  getCoverStyle(f: any): { [key: string]: string } {
    if (f?.imageCouverture) {
      return {
        'background-image': `url(${f.imageCouverture})`,
        'background-size': 'cover',
        'background-position': 'center'
      };
    }
    const color = f?.categorieCouleur || '#8B3A3A';
    return { 'background': `linear-gradient(135deg, ${color}30 0%, ${color}60 100%)` };
  }

  logout() {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
    localStorage.clear();
    this.router.navigate(['/login']);
  }
}