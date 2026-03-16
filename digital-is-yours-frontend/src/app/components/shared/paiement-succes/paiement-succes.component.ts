import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-paiement-succes',
  templateUrl: './paiement-succes.component.html',
  styleUrls: ['./paiement-succes.component.css']
})
export class PaiementSuccesComponent implements OnInit {
  chargement = true;
  reference  = '';
  erreur     = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    const sessionId   = this.route.snapshot.queryParamMap.get('session_id');
    const formationId = this.route.snapshot.queryParamMap.get('formation_id');
    const token       = localStorage.getItem('token');

    if (!sessionId || !formationId || !token) {
      this.router.navigate(['/']);
      return;
    }

    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });

    this.http.post<any>(
      `http://localhost:8080/api/apprenant/formations/${formationId}/stripe/confirmer`,
      { sessionId },
      { headers }
    ).subscribe({
      next: (res) => {
        this.reference  = res.referencePaiement || 'STRIPE-OK';
        this.chargement = false;
      },
      error: () => {
        this.reference  = 'STRIPE-OK';
        this.chargement = false;
      }
    });
  }

  allerDashboard(): void {
  this.router.navigate(['/apprenant/dashboard'], { 
    queryParams: { tab: 'mes-formations' } 
  });
  }}