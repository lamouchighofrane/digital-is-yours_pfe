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
  apprenantsStatut: any[] = [];
  filtreStatutForm = 'ALL';
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
      .subscribe({
        next: d => { this.users = d; this.isLoading = false; },
        error: () => this.isLoading = false
      });
  }
  loadApprenantsStatut() {
  this.http.get<any[]>(`${this.api}/apprenants-statut`, { headers: this.headers() })
    .subscribe({
      next: d => { this.apprenantsStatut = d; },
      error: () => {}
    });
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
 get apprenants() {
  let list = this.filterList(this.users.filter(u => u.role === 'APPRENANT'));
  if (this.filtreStatutForm === 'ALL') return list;
  if (this.filtreStatutForm === 'AUCUNE') {
    return list.filter(u => this.getStatutFormation(u.id).length === 0);
  }
  return list.filter(u =>
    this.getStatutFormation(u.id).some(f => f.statutEnrichi === this.filtreStatutForm)
  );
}
  get pendingFormateurs() { return this.users.filter(u => u.role === 'FORMATEUR' && !u.emailVerifie); }

  getCurrentList() {
    if (this.activeSection === 'formateurs') return this.formateurs;
    if (this.activeSection === 'apprenants') return this.apprenants;
    return this.filteredUsers;
  }
  getStatutFormation(userId: number): any[] {
  return this.apprenantsStatut.filter(a => a.id === userId);
}

getStatutEnrichiLabel(s: string): string {
  const map: any = {
    'CERTIFIE': 'Certifié',
    'A_RISQUE': 'À risque',
    'TERMINE':  'Terminé',
    'EN_COURS': 'En cours',
    'A_FAIRE':  'À faire'
  };
  return map[s] || '—';
}

getStatutEnrichiClass(s: string): string {
  const map: any = {
    'CERTIFIE': 'se-certifie',
    'A_RISQUE': 'se-risque',
    'TERMINE':  'se-termine',
    'EN_COURS': 'se-encours',
    'A_FAIRE':  'se-afaire'
  };
  return map[s] || '';
}

getProgressionColor(p: number): string {
  if (p >= 80) return '#27ae60';
  if (p >= 40) return '#f39c12';
  return '#e74c3c';
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
  this.filtreStatutForm = 'ALL';
  if (s !== 'dashboard' && s !== 'categories') this.loadUsers();
  if (s === 'apprenants') this.loadApprenantsStatut();
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

          // ✅ FIX BUG 4 : Si le backend retourne l'utilisateur créé, l'ajouter directement
          // dans this.users sans attendre le rechargement réseau
          if (r && r.id) {
            // Le backend retourne l'utilisateur complet → ajout immédiat
            this.users = [r, ...this.users];
            this.loadStats();
          } else {
            // Le backend retourne juste {message: "..."} → délai pour laisser MySQL committer
            setTimeout(() => {
              this.loadUsers();
              this.loadStats();
            }, 300);
          }

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
  const list = this.getCurrentList();
  const h = ['Prénom', 'Nom', 'Email', 'Téléphone', 'Rôle', 'Statut compte', 'Formation', 'Progression', 'Statut formation'];
  const rows = list.map(u => {
    const formations = this.getStatutFormation(u.id);
    if (this.activeSection === 'apprenants' && formations.length > 0) {
      return formations.map(f => [
        u.prenom,
        u.nom,
        u.email,
        u.telephone || '',
        this.getRoleLabel(u.role),
        this.getStatusLabel(u),
        f.formationTitre || '',
        (f.progression || 0).toFixed(0) + '%',
        this.getStatutEnrichiLabel(f.statutEnrichi)
      ].join(','));
    }
    return [[
      u.prenom,
      u.nom,
      u.email,
      u.telephone || '',
      this.getRoleLabel(u.role),
      this.getStatusLabel(u),
      'Aucune formation',
      '',
      ''
    ].join(',')];
  }).flat();

  const csv = '\uFEFF' + [h.join(','), ...rows].join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
  a.download = `export_${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  this.showToast('Export CSV téléchargé');
}

  exportPDF() {
  const w = window.open('', '_blank');
  if (!w) return;
  const list = this.getCurrentList();

  let rows = '';
  list.forEach(u => {
    const formations = this.activeSection === 'apprenants'
      ? this.getStatutFormation(u.id) : [];

    if (formations.length > 0) {
      formations.forEach((f, i) => {
        rows += `<tr>
          ${i === 0 ? `<td rowspan="${formations.length}">
            <strong>${u.prenom} ${u.nom}</strong><br>
            <span style="color:#6B5F52;font-size:11px">${u.dateInscription ? new Date(u.dateInscription).toLocaleDateString('fr-FR') : ''}</span>
          </td>
          <td rowspan="${formations.length}" style="color:#6B5F52">${u.email}</td>
          <td rowspan="${formations.length}" style="color:#6B5F52">${u.telephone || '—'}</td>
          <td rowspan="${formations.length}"><span class="role-chip">${this.getRoleLabel(u.role)}</span></td>
          <td rowspan="${formations.length}"><span class="status-chip ${this.getStatusClass(u)}">${this.getStatusLabel(u)}</span></td>` : ''}
          <td style="font-size:11px;font-weight:600">${f.formationTitre || ''}</td>
          <td>
            <div style="display:flex;align-items:center;gap:6px">
              <div style="width:60px;height:5px;background:#E8E3DB;border-radius:3px;overflow:hidden">
                <div style="width:${f.progression || 0}%;height:100%;background:${this.getProgressionColor(f.progression || 0)};border-radius:3px"></div>
              </div>
              <span style="font-size:11px;font-weight:600">${(f.progression || 0).toFixed(0)}%</span>
            </div>
          </td>
          <td><span class="se-chip ${this.getStatutEnrichiClass(f.statutEnrichi)}">${this.getStatutEnrichiLabel(f.statutEnrichi)}</span></td>
        </tr>`;
      });
    } else {
      const colspan = this.activeSection === 'apprenants' ? '' : '';
      rows += `<tr>
        <td><strong>${u.prenom} ${u.nom}</strong><br>
          <span style="color:#6B5F52;font-size:11px">${u.dateInscription ? new Date(u.dateInscription).toLocaleDateString('fr-FR') : ''}</span>
        </td>
        <td style="color:#6B5F52">${u.email}</td>
        <td style="color:#6B5F52">${u.telephone || '—'}</td>
        <td><span class="role-chip">${this.getRoleLabel(u.role)}</span></td>
        <td><span class="status-chip ${this.getStatusClass(u)}">${this.getStatusLabel(u)}</span></td>
        ${this.activeSection === 'apprenants' ? '<td colspan="3" style="color:#9B8B6E;font-size:12px">Aucune formation</td>' : ''}
      </tr>`;
    }
  });

  const formationHeaders = this.activeSection === 'apprenants'
    ? '<th>Formation</th><th>Progression</th><th>Statut formation</th>'
    : '';

  w.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"/>
  <title>Export</title>
  <style>
    body{font-family:sans-serif;padding:32px;color:#1A1612}
    h1{color:#8B3A3A;font-size:20px;margin-bottom:4px}
    p{color:#6B5F52;font-size:12px;margin-bottom:20px}
    table{width:100%;border-collapse:collapse;font-size:11px}
    th{background:#8B3A3A;color:#fff;padding:9px 12px;text-align:left}
    td{padding:8px 12px;border-bottom:1px solid #E8E3DB;vertical-align:middle}
    tr:nth-child(even) td{background:#F5F1EB}
    .role-chip{background:#f9f0f0;color:#8B3A3A;padding:2px 8px;border-radius:10px;font-size:10px;font-weight:700}
    .sc-active{background:#e8f5e9;color:#2e7d32;padding:2px 8px;border-radius:10px;font-size:10px;font-weight:700}
    .sc-inactive{background:#fce4e4;color:#c0392b;padding:2px 8px;border-radius:10px;font-size:10px;font-weight:700}
    .sc-pending{background:#fff3cd;color:#e67e22;padding:2px 8px;border-radius:10px;font-size:10px;font-weight:700}
    .se-chip{padding:2px 8px;border-radius:10px;font-size:10px;font-weight:700}
    .se-certifie{background:rgba(139,92,246,.12);color:#7C3AED}
    .se-risque{background:#fce4e4;color:#c0392b}
    .se-termine{background:#e8f5e9;color:#2e7d32}
    .se-encours{background:rgba(74,124,126,.12);color:#4A7C7E}
    .se-afaire{background:rgba(155,139,110,.1);color:#9B8B6E}
  </style>
  </head><body>
  <h1>Digital Is Yours — ${this.activeSection === 'apprenants' ? 'Apprenants' : this.activeSection === 'formateurs' ? 'Formateurs' : 'Utilisateurs'}</h1>
  <p>${new Date().toLocaleDateString('fr-FR')} · ${list.length} résultat(s)</p>
  <table>
    <thead>
      <tr>
        <th>Nom</th><th>Email</th><th>Tél.</th><th>Rôle</th><th>Statut compte</th>
        ${formationHeaders}
      </tr>
    </thead>
    <tbody>${rows}</tbody>
  </table>
  <script>window.onload=()=>window.print()<\/script>
  </body></html>`);
  w.document.close();
  this.showToast('PDF ouvert');
}

  // ── Helpers ───────────────────────────────────────────────
  countActive(l: any[])   { return l.filter(u => u.active && u.emailVerifie).length; }
  countInactive(l: any[]) { return l.filter(u => !u.active).length; }
  countStatutFormation(statut: string): number {
  const list = this.apprenants;
  if (statut === 'AUCUNE') {
    return list.filter(u => this.getStatutFormation(u.id).length === 0).length;
  }
  return list.filter(u =>
    this.getStatutFormation(u.id).some(f => f.statutEnrichi === statut)
  ).length;
}
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
