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
  stats: any = { totalUsers: 0, apprenants: 0, formateurs: 0, desactives: 0 };
  users: any[] = [];
  searchTerm   = '';
  filterStatus = 'ALL';

  showCreateModal = false;
  createError = '';
  createForm: any = { prenom: '', nom: '', email: '', telephone: '', password: '', role: 'APPRENANT' };

  showEditModal = false;
  editError = '';
  editingUser: any = null;
  editForm: any = {};

  toast: { msg: string; type: 'success' | 'error' } | null = null;

  private api = 'http://localhost:8080/api/admin';

  constructor(private router: Router, private http: HttpClient) {}

  ngOnInit() {
    const token = localStorage.getItem('admin_token');
    if (!token) { this.router.navigate(['/admin-login']); return; }
    this.adminUser = JSON.parse(localStorage.getItem('admin_user') || '{}');
    this.loadStats();
    this.loadUsers();
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
      .subscribe({ next: d => this.stats = d, error: () => this.logout() });
  }

  loadUsers() {
    this.isLoading = true;
    this.http.get<any[]>(`${this.api}/users`, { headers: this.headers() })
      .subscribe({ next: d => { this.users = d; this.isLoading = false; }, error: () => this.isLoading = false });
  }

  // ── Filtrage ──────────────────────────────────────────────
  private filterList(list: any[]): any[] {
    return list.filter(u => {
      const s = this.filterStatus;
      const okS = s === 'ALL'
        || (s === 'ACTIF'     && u.active && u.emailVerifie)
        || (s === 'DESACTIVE' && !u.active)
        || (s === 'ATTENTE'   && !u.emailVerifie);
      const okQ = !this.searchTerm
        || `${u.prenom} ${u.nom} ${u.email}`.toLowerCase().includes(this.searchTerm.toLowerCase());
      return okS && okQ;
    });
  }

  get filteredUsers()     { return this.filterList(this.users); }
  get formateurs()        { return this.filterList(this.users.filter(u => u.role === 'FORMATEUR')); }
  get apprenants()        { return this.filterList(this.users.filter(u => u.role === 'APPRENANT')); }
  get pendingFormateurs() { return this.users.filter(u => u.role === 'FORMATEUR' && !u.emailVerifie); }

  getCurrentList() {
    if (this.activeSection === 'formateurs') return this.formateurs;
    if (this.activeSection === 'apprenants') return this.apprenants;
    return this.filteredUsers;
  }

  // ── Titre dynamique bouton ────────────────────────────────
  getCreateTitle(): string {
    if (this.activeSection === 'formateurs') return 'Créer un formateur';
    if (this.activeSection === 'apprenants') return 'Créer un apprenant';
    return 'Créer un utilisateur';
  }

  setSection(s: string) {
    this.activeSection = s;
    this.searchTerm = '';
    this.filterStatus = 'ALL';
    if (s !== 'dashboard' && s !== 'categories') this.loadUsers();
  }

  // ── Créer ─────────────────────────────────────────────────
  openCreateModal(forceRole?: string) {
    let role = 'APPRENANT';
    if (forceRole) role = forceRole;
    else if (this.activeSection === 'formateurs') role = 'FORMATEUR';
    this.createForm = { prenom: '', nom: '', email: '', telephone: '', password: '', role };
    this.createError = '';
    this.showCreateModal = true;
  }

  createUser() {
    this.createError = '';
    if (!this.createForm.prenom || !this.createForm.nom || !this.createForm.email || !this.createForm.password) {
      this.createError = 'Veuillez remplir tous les champs obligatoires (*)'; return;
    }
    if (this.createForm.password.length < 8) {
      this.createError = 'Le mot de passe doit avoir au moins 8 caractères'; return;
    }
    this.http.post<any>(`${this.api}/users`, this.createForm, { headers: this.headers() })
      .subscribe({
        next: r => {
          this.showCreateModal = false;
          this.loadUsers(); this.loadStats();
          this.showToast(r.message || `${this.getCreateTitle()} — succès`);
        },
        error: e => this.createError = e.error?.message || 'Erreur lors de la création'
      });
  }

  // ── Modifier ──────────────────────────────────────────────
  openEditModal(u: any) {
    this.editingUser = u;
    this.editForm = { prenom: u.prenom, nom: u.nom, email: u.email, telephone: u.telephone || '', password: '', role: u.role };
    this.editError = '';
    this.showEditModal = true;
  }

  updateUser() {
    this.editError = '';
    if (!this.editForm.prenom || !this.editForm.nom || !this.editForm.email) {
      this.editError = 'Prénom, nom et email sont obligatoires'; return;
    }
    const payload: any = { ...this.editForm };
    if (!payload.password) delete payload.password;
    this.http.put<any>(`${this.api}/users/${this.editingUser.id}`, payload, { headers: this.headers() })
      .subscribe({
        next: r => {
          this.showEditModal = false;
          this.loadUsers();
          this.showToast(r.message || 'Utilisateur modifié avec succès');
        },
        error: e => this.editError = e.error?.message || 'Erreur'
      });
  }

  toggleActive(u: any) {
    if (!confirm(`${u.active ? 'Désactiver' : 'Activer'} le compte de ${u.prenom} ${u.nom} ?`)) return;
    this.http.patch<any>(`${this.api}/users/${u.id}/toggle-active`, {}, { headers: this.headers() })
      .subscribe({
        next: r => { u.active = r.active; this.loadStats(); this.showToast(r.active ? 'Compte activé' : 'Compte désactivé'); },
        error: e => this.showToast(e.error?.message || 'Erreur', 'error')
      });
  }

  deleteUser(u: any) {
    if (!confirm(`Supprimer définitivement ${u.prenom} ${u.nom} ?`)) return;
    this.http.delete(`${this.api}/users/${u.id}`, { headers: this.headers() })
      .subscribe({
        next: () => { this.users = this.users.filter(x => x.id !== u.id); this.loadStats(); this.showToast('Utilisateur supprimé'); },
        error: e => this.showToast(e.error?.message || 'Erreur', 'error')
      });
  }

  approveFormateur(u: any) {
    this.http.patch<any>(`${this.api}/users/${u.id}/approve-formateur`, {}, { headers: this.headers() })
      .subscribe({
        next: () => { u.emailVerifie = true; u.active = true; this.loadStats(); this.showToast(`${u.prenom} approuvé comme formateur`); }
      });
  }

  rejectFormateur(u: any) {
    if (!confirm(`Refuser ${u.prenom} ${u.nom} ?`)) return;
    this.http.delete(`${this.api}/users/${u.id}/reject-formateur`, { headers: this.headers() })
      .subscribe({ next: () => { this.users = this.users.filter(x => x.id !== u.id); this.loadStats(); this.showToast('Formateur refusé'); } });
  }

  exportCSV() {
    const h = ['Prénom','Nom','Email','Téléphone','Rôle','Statut'];
    const rows = this.getCurrentList().map(u => [u.prenom, u.nom, u.email, u.telephone||'', u.role, this.getStatusLabel(u)]);
    const csv = [h, ...rows].map(r => r.join(',')).join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    a.download = `export_${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
    this.showToast('Export CSV téléchargé');
  }

  exportPDF() {
    const w = window.open('', '_blank'); if (!w) return;
    const list = this.getCurrentList();
    const rows = list.map(u =>
      `<tr><td>${u.prenom} ${u.nom}</td><td>${u.email}</td><td>${u.telephone||'—'}</td><td>${this.getRoleLabel(u.role)}</td><td>${this.getStatusLabel(u)}</td></tr>`
    ).join('');
    w.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"/><title>Export</title>
    <style>body{font-family:sans-serif;padding:32px;color:#1A1612}h1{color:#8B3A3A;font-size:20px;margin-bottom:4px}
    p{color:#6B5F52;font-size:12px;margin-bottom:20px}table{width:100%;border-collapse:collapse;font-size:12px}
    th{background:#8B3A3A;color:#fff;padding:10px 14px;text-align:left}
    td{padding:9px 14px;border-bottom:1px solid #E8E3DB}tr:nth-child(even) td{background:#F5F1EB}</style>
    </head><body>
    <h1>Digital Is Yours — Utilisateurs</h1>
    <p>${new Date().toLocaleDateString('fr-FR')} · ${list.length} résultat(s)</p>
    <table><thead><tr><th>Nom</th><th>Email</th><th>Tél.</th><th>Rôle</th><th>Statut</th></tr></thead>
    <tbody>${rows}</tbody></table>
    <script>window.onload=()=>window.print()<\/script></body></html>`);
    w.document.close();
    this.showToast('PDF ouvert');
  }

  // ── Helpers ───────────────────────────────────────────────
  countActive(l: any[])   { return l.filter(u => u.active && u.emailVerifie).length; }
  countInactive(l: any[]) { return l.filter(u => !u.active).length; }
  countPending(l: any[])  { return l.filter(u => !u.emailVerifie).length; }
  getInitials(u: any)     { return ((u?.prenom?.[0]||'') + (u?.nom?.[0]||'')).toUpperCase() || '?'; }
  getRoleLabel(r: string) { return r === 'APPRENANT' ? 'Apprenant' : r === 'FORMATEUR' ? 'Formateur' : r; }
  getStatusLabel(u: any)  { return u.active && u.emailVerifie ? 'Actif' : !u.emailVerifie ? 'En attente' : 'Désactivé'; }
  getStatusClass(u: any)  { return u.active && u.emailVerifie ? 'sc-active' : !u.emailVerifie ? 'sc-pending' : 'sc-inactive'; }
  get adminInitials()     { return ((this.adminUser?.prenom?.[0]||'') + (this.adminUser?.nom?.[0]||'')).toUpperCase() || 'AD'; }

  logout() {
    localStorage.clear();
    this.router.navigate(['/admin-login']);
  }
}