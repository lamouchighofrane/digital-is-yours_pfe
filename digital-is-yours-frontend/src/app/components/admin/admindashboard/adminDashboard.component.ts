import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './adminDashboard.component.html',
  styleUrls: ['./adminDashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {

  activeSection = 'stats';
  adminUser: any = null;
  stats: any = { totalUsers: 0, apprenants: 0, formateurs: 0, nonVerifies: 0 };
  users: any[] = [];
  isLoading = false;
  searchTerm = '';
  filterRole = 'ALL';

  private apiUrl = 'http://localhost:8080/api/admin';

  constructor(private router: Router, private http: HttpClient) {}

  ngOnInit() {
    const token = localStorage.getItem('admin_token');
    if (!token) {
      this.router.navigate(['/admin-login']);
      return;
    }
    this.adminUser = JSON.parse(localStorage.getItem('admin_user') || '{}');
    this.loadStats();
    this.loadUsers();
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('admin_token');
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  loadStats() {
    this.http.get<any>(`${this.apiUrl}/stats`, { headers: this.getHeaders() }).subscribe({
      next: (data) => this.stats = data,
      error: () => this.logout()
    });
  }

  loadUsers() {
    this.isLoading = true;
    this.http.get<any[]>(`${this.apiUrl}/users`, { headers: this.getHeaders() }).subscribe({
      next: (data) => { this.users = data; this.isLoading = false; },
      error: () => { this.isLoading = false; }
    });
  }

  get filteredUsers() {
    return this.users.filter(u => {
      const matchRole = this.filterRole === 'ALL' || u.role === this.filterRole;
      const matchSearch = !this.searchTerm ||
        u.prenom?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        u.nom?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        u.email?.toLowerCase().includes(this.searchTerm.toLowerCase());
      return matchRole && matchSearch;
    });
  }

  get apprenants() {
    return this.users.filter(u => u.role === 'APPRENANT');
  }

  get formateurs() {
    return this.users.filter(u => u.role === 'FORMATEUR');
  }

  get formateursNonVerifies() {
    return this.users.filter(u => u.role === 'FORMATEUR' && !u.emailVerifie);
  }

  toggleActive(user: any) {
    this.http.patch<any>(`${this.apiUrl}/users/${user.id}/toggle-active`, {}, { headers: this.getHeaders() }).subscribe({
      next: (res) => {
        user.active = res.active;
      }
    });
  }

  deleteUser(user: any) {
    if (!confirm(`Supprimer ${user.prenom} ${user.nom} ?`)) return;
    this.http.delete(`${this.apiUrl}/users/${user.id}`, { headers: this.getHeaders() }).subscribe({
      next: () => this.users = this.users.filter(u => u.id !== user.id)
    });
  }

  approveFormateur(user: any) {
    this.http.patch<any>(`${this.apiUrl}/users/${user.id}/approve-formateur`, {}, { headers: this.getHeaders() }).subscribe({
      next: () => {
        user.emailVerifie = true;
        user.active = true;
      }
    });
  }

  rejectFormateur(user: any) {
    if (!confirm(`Refuser et supprimer ${user.prenom} ${user.nom} ?`)) return;
    this.http.delete(`${this.apiUrl}/users/${user.id}/reject-formateur`, { headers: this.getHeaders() }).subscribe({
      next: () => this.users = this.users.filter(u => u.id !== user.id)
    });
  }

  setSection(section: string) {
    this.activeSection = section;
    if (section === 'stats') this.loadStats();
    if (section !== 'stats') this.loadUsers();
  }

  logout() {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_user');
    this.router.navigate(['/admin-login']);
  }

  getRoleLabel(role: string): string {
    return role === 'APPRENANT' ? 'Apprenant' : role === 'FORMATEUR' ? 'Formateur' : role;
  }

  getInitials(user: any): string {
    return ((user.prenom?.[0] || '') + (user.nom?.[0] || '')).toUpperCase();
  }
}