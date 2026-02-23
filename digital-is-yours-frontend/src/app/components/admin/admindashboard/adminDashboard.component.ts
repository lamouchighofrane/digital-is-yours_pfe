import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './adminDashboard.component.html',
  styleUrls: ['./adminDashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {

  activeSection = 'dashboard';
  adminUser: any = null;
  isLoading = false;

  // Stats
  stats: any = { totalUsers: 0, apprenants: 0, formateurs: 0, nonVerifies: 0, desactives: 0 };

  // Users
  users: any[] = [];
  searchTerm = '';
  filterRole = 'ALL';
  filterStatus = 'ALL';

  // Modal création
  showCreateModal = false;
  createError = '';
  createForm = { prenom: '', nom: '', email: '', telephone: '', password: '', role: 'APPRENANT' };

  // Modal édition
  showEditModal = false;
  editError = '';
  editingUser: any = null;
  editForm: any = { prenom: '', nom: '', email: '', telephone: '', password: '', role: 'APPRENANT' };

  // Toast notification
  toast: { msg: string; type: 'success' | 'error' } | null = null;

  private apiUrl = 'http://localhost:8080/api/admin';

  constructor(private router: Router, private http: HttpClient) {}

  ngOnInit() {
    const token = localStorage.getItem('admin_token');
    if (!token) { this.router.navigate(['/admin-login']); return; }
    this.adminUser = JSON.parse(localStorage.getItem('admin_user') || '{}');
    this.loadStats();
    this.loadUsers();
  }

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('admin_token')}` });
  }

  // ── Toast ─────────────────────────────────────────────────────
  showToast(msg: string, type: 'success' | 'error' = 'success') {
    this.toast = { msg, type };
    setTimeout(() => this.toast = null, 3500);
  }

  // ── Stats ─────────────────────────────────────────────────────
  loadStats() {
    this.http.get<any>(`${this.apiUrl}/stats`, { headers: this.getHeaders() })
      .subscribe({ next: d => this.stats = d, error: () => this.logout() });
  }

  // ── Users ─────────────────────────────────────────────────────
  loadUsers() {
    this.isLoading = true;
    this.http.get<any[]>(`${this.apiUrl}/users`, { headers: this.getHeaders() })
      .subscribe({
        next: d => { this.users = d; this.isLoading = false; },
        error: () => { this.isLoading = false; }
      });
  }

  get filteredUsers() {
    return this.users.filter(u => {
      const matchRole   = this.filterRole === 'ALL' || u.role === this.filterRole;
      const matchStatus = this.filterStatus === 'ALL'
        || (this.filterStatus === 'ACTIF'     && u.active && u.emailVerifie)
        || (this.filterStatus === 'DESACTIVE' && !u.active)
        || (this.filterStatus === 'ATTENTE'   && !u.emailVerifie);
      const matchSearch = !this.searchTerm ||
        `${u.prenom} ${u.nom} ${u.email}`.toLowerCase().includes(this.searchTerm.toLowerCase());
      return matchRole && matchStatus && matchSearch;
    });
  }

  get pendingFormateurs() { return this.users.filter(u => u.role === 'FORMATEUR' && !u.emailVerifie); }
  get formateurs()        { return this.users.filter(u => u.role === 'FORMATEUR'); }
  get apprenants()        { return this.users.filter(u => u.role === 'APPRENANT'); }

  // ── Créer ─────────────────────────────────────────────────────
  openCreateModal() {
    this.createForm = { prenom: '', nom: '', email: '', telephone: '', password: '', role: 'APPRENANT' };
    this.createError = '';
    this.showCreateModal = true;
  }

  createUser() {
    this.createError = '';
    if (!this.createForm.prenom || !this.createForm.nom || !this.createForm.email || !this.createForm.password) {
      this.createError = 'Veuillez remplir tous les champs obligatoires (*).'; return;
    }
    if (this.createForm.password.length < 8) {
      this.createError = 'Le mot de passe doit avoir au moins 8 caractères.'; return;
    }
    this.http.post<any>(`${this.apiUrl}/users`, this.createForm, { headers: this.getHeaders() })
      .subscribe({
        next: r => {
          this.showCreateModal = false;
          this.loadUsers(); this.loadStats();
          this.showToast(r.message || 'Utilisateur créé avec succès');
        },
        error: e => { this.createError = e.error?.message || 'Erreur lors de la création.'; }
      });
  }

  // ── Modifier ──────────────────────────────────────────────────
  openEditModal(u: any) {
    this.editingUser = u;
    this.editForm = { prenom: u.prenom, nom: u.nom, email: u.email, telephone: u.telephone || '', password: '', role: u.role };
    this.editError = '';
    this.showEditModal = true;
  }

  updateUser() {
    this.editError = '';
    if (!this.editForm.prenom || !this.editForm.nom || !this.editForm.email) {
      this.editError = 'Prénom, nom et email sont obligatoires.'; return;
    }
    const payload: any = { ...this.editForm };
    if (!payload.password) delete payload.password;

    this.http.put<any>(`${this.apiUrl}/users/${this.editingUser.id}`, payload, { headers: this.getHeaders() })
      .subscribe({
        next: r => {
          this.showEditModal = false;
          this.loadUsers();
          this.showToast(r.message || 'Utilisateur modifié');
        },
        error: e => { this.editError = e.error?.message || 'Erreur lors de la modification.'; }
      });
  }

  // ── Toggle activer/désactiver ─────────────────────────────────
  toggleActive(u: any) {
    const action = u.active ? 'désactiver' : 'activer';
    if (!confirm(`Voulez-vous ${action} le compte de ${u.prenom} ${u.nom} ?`)) return;

    this.http.patch<any>(`${this.apiUrl}/users/${u.id}/toggle-active`, {}, { headers: this.getHeaders() })
      .subscribe({
        next: r => {
          u.active = r.active;
          this.loadStats();
          const msg = r.active ? `✓ Compte de ${u.prenom} activé` : `Compte de ${u.prenom} désactivé`;
          this.showToast(msg, r.active ? 'success' : 'error');
        },
        error: e => {
          const status = e.status;
          let msg = 'Erreur lors du toggle';
          if (status === 403) msg = 'Accès refusé (403) — vérifiez SecurityConfig';
          else if (status === 401) msg = 'Non authentifié (401) — reconnectez-vous';
          else if (status === 500) msg = 'Erreur serveur (500) — vérifiez les logs Spring';
          else if (e.error?.message) msg = e.error.message;
          this.showToast(msg, 'error');
          console.error('Toggle error:', e);
        }
      });
  }

  // ── Supprimer ─────────────────────────────────────────────────
  deleteUser(u: any) {
    if (!confirm(`Supprimer définitivement ${u.prenom} ${u.nom} ?`)) return;
    this.http.delete(`${this.apiUrl}/users/${u.id}`, { headers: this.getHeaders() })
      .subscribe({
        next: () => {
          this.users = this.users.filter(x => x.id !== u.id);
          this.loadStats();
          this.showToast('Utilisateur supprimé');
        },
        error: e => this.showToast(e.error?.message || 'Erreur suppression', 'error')
      });
  }

  // ── Approuver / Refuser formateur ─────────────────────────────
  approveFormateur(u: any) {
    this.http.patch<any>(`${this.apiUrl}/users/${u.id}/approve-formateur`, {}, { headers: this.getHeaders() })
      .subscribe({
        next: () => { u.emailVerifie = true; u.active = true; this.loadStats(); this.showToast('Formateur approuvé'); }
      });
  }

  rejectFormateur(u: any) {
    if (!confirm(`Refuser et supprimer ${u.prenom} ${u.nom} ?`)) return;
    this.http.delete(`${this.apiUrl}/users/${u.id}/reject-formateur`, { headers: this.getHeaders() })
      .subscribe({
        next: () => { this.users = this.users.filter(x => x.id !== u.id); this.loadStats(); this.showToast('Formateur refusé'); }
      });
  }

  // ── Export CSV ────────────────────────────────────────────────
  exportCSV() {
    const headers = ['Prénom', 'Nom', 'Email', 'Téléphone', 'Rôle', 'Statut', 'Date inscription'];
    const rows = this.filteredUsers.map(u => [
      u.prenom, u.nom, u.email, u.telephone || '',
      u.role,
      u.active && u.emailVerifie ? 'Actif' : !u.emailVerifie ? 'En attente' : 'Désactivé',
      u.dateInscription ? new Date(u.dateInscription).toLocaleDateString('fr-FR') : ''
    ]);
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `utilisateurs_${new Date().toISOString().slice(0,10)}.csv`;
    link.click();
    this.showToast('Export CSV téléchargé');
  }

  // ── Export PDF ───────────────────────────────────────────────
  exportPDF() {
    const win = window.open('', '_blank');
    if (!win) return;
    const list = this.filteredUsers;
    const date = new Date().toLocaleDateString('fr-FR');
    const rows = list.map(u => `
      <tr>
        <td>${u.prenom} ${u.nom}</td>
        <td>${u.email}</td>
        <td>${u.telephone || '—'}</td>
        <td>${this.getRoleLabel(u.role)}</td>
        <td>${this.getStatusLabel(u)}</td>
        <td>${u.dateInscription ? new Date(u.dateInscription).toLocaleDateString('fr-FR') : '—'}</td>
      </tr>`).join('');

    win.document.write(`<!DOCTYPE html><html><head>
      <meta charset="UTF-8"/>
      <title>Utilisateurs — Digital Is Yours</title>
      <style>
        body { font-family: 'Segoe UI', sans-serif; padding: 32px; color: #1A1612; }
        h1 { font-size: 22px; color: #8B3A3A; margin-bottom: 4px; }
        p  { font-size: 12px; color: #9E9082; margin-bottom: 20px; }
        table { width: 100%; border-collapse: collapse; font-size: 12px; }
        th { background: #8B3A3A; color: white; padding: 10px 12px; text-align: left; }
        td { padding: 9px 12px; border-bottom: 1px solid #EDE8DF; }
        tr:nth-child(even) td { background: #F5F1EB; }
        .footer { margin-top: 24px; font-size: 11px; color: #9E9082; }
      </style></head><body>
      <h1>Digital Is Yours — Liste des Utilisateurs</h1>
      <p>Exporté le ${date} · ${list.length} utilisateur(s)</p>
      <table>
        <thead><tr>
          <th>Nom complet</th><th>Email</th><th>Téléphone</th>
          <th>Rôle</th><th>Statut</th><th>Date inscription</th>
        </tr></thead>
        <tbody>${rows}</tbody>
      </table>
      <div class="footer">Digital Is Yours — Panneau d'administration</div>
      <script>window.onload = () => { window.print(); }<\/script>
    </body></html>`);
    win.document.close();
    this.showToast('Export PDF ouvert dans un nouvel onglet');
  }

  // ── Compteurs pour mini-KPI ────────────────────────────
  countActive(list: any[]): number   { return list.filter(u => u.active && u.emailVerifie).length; }
  countInactive(list: any[]): number { return list.filter(u => !u.active).length; }
  countPending(list: any[]): number  { return list.filter(u => !u.emailVerifie).length; }

  // ── Helpers ───────────────────────────────────────────────────
  getInitials(u: any): string {
    return ((u?.prenom?.[0] || '') + (u?.nom?.[0] || '')).toUpperCase() || '?';
  }

  getRoleLabel(r: string): string {
    return r === 'APPRENANT' ? 'Apprenant' : r === 'FORMATEUR' ? 'Formateur' : r;
  }

  getStatusLabel(u: any): string {
    if (u.active && u.emailVerifie) return 'Actif';
    if (!u.emailVerifie) return 'En attente';
    return 'Désactivé';
  }

  getStatusClass(u: any): string {
    if (u.active && u.emailVerifie) return 's-active';
    if (!u.emailVerifie) return 's-pending';
    return 's-inactive';
  }

  get adminInitials(): string {
    return ((this.adminUser?.prenom?.[0] || '') + (this.adminUser?.nom?.[0] || '')).toUpperCase() || 'AD';
  }

  setSection(s: string) { this.activeSection = s; if (s !== 'dashboard') this.loadUsers(); }

  logout() {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_user');
    this.router.navigate(['/admin-login']);
  }
}