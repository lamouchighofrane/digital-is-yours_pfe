import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'app-formation-detail',
  templateUrl: './formation-detail.component.html',
  styleUrls: ['./formation-detail.component.css']
})
export class FormationDetailComponent implements OnInit {

  formation: any = null;
  isLoading     = true;
  error         = '';

  // Inscription / statut
  estInscrit     = false;
  checkingStatut = false;

  // ── PAIEMENT (modal — conservé pour compatibilité HTML) ───
  showPaiementModal    = false;
  paiementEtape: 'formulaire' | 'traitement' | 'succes' | 'echec' = 'formulaire';
  paiementReference    = '';
  paiementMontant      = 0;
  paiementErreur       = '';
  showCvv              = false;

  // ── Redirection Stripe ────────────────────────────────────
  chargement        = false;
  redirectionStripe = false;

  carteForm = {
    numeroCarte: '',
    nomCarte:    '',
    expiration:  '',
    cvv:         ''
  };

  private api           = 'http://localhost:8080/api/apprenant/formations';
  private formationsApi = 'http://localhost:8080/api/admin/formations';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadFormation(+id);
  }

  private headers() {
    const token = localStorage.getItem('token');
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  private isConnected(): boolean {
    return !!localStorage.getItem('token');
  }

  loadFormation(id: number) {
    this.isLoading = true;
    this.http.get<any>(`${this.formationsApi}/${id}`, { headers: this.headers() })
      .subscribe({
        next: f => {
          this.formation = f;
          this.isLoading = false;
          if (this.isConnected()) this.checkStatutInscription(id);
          this.cdr.detectChanges();
        },
        error: () => {
          this.error     = 'Formation introuvable.';
          this.isLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

  checkStatutInscription(formationId: number) {
    this.checkingStatut = true;
    this.http.get<any>(`${this.api}/${formationId}/statut-inscription`, { headers: this.headers() })
      .subscribe({
        next: res => {
          this.estInscrit     = res.inscrit;
          this.checkingStatut = false;
          this.cdr.detectChanges();
        },
        error: () => { this.checkingStatut = false; }
      });
  }

  // ── S'INSCRIRE → Redirection Stripe Checkout ─────────────

  sInscrire() {
    if (!this.isConnected()) {
      this.router.navigate(['/login']);
      return;
    }

    this.chargement        = true;
    this.redirectionStripe = true;

    this.http.post<any>(
      `${this.api}/${this.formation.id}/stripe/checkout`,
      {},
      { headers: this.headers() }
    ).subscribe({
      next: (res) => {
        // Redirection vers la page Stripe officielle
        window.location.href = res.checkoutUrl;
      },
      error: (err) => {
        this.chargement        = false;
        this.redirectionStripe = false;
        const msg = err.error?.error || err.error?.message || 'Impossible de démarrer le paiement';
        if (msg.includes('déjà inscrit')) {
          this.estInscrit = true;
        } else {
          alert(msg);
        }
        this.cdr.detectChanges();
      }
    });
  }

  accederFormation() {
    this.router.navigate(['/apprenant/dashboard']);
  }

  // ── MODAL PAIEMENT (conservé pour compatibilité HTML) ─────

  fermerModal() {
    this.showPaiementModal = false;
    this.paiementEtape     = 'formulaire';
    this.paiementErreur    = '';
    this.cdr.detectChanges();
  }

  payerFormation() {
    if (!this.formation) return;
    const formationId = this.formation.id;

    this.paiementEtape  = 'traitement';
    this.paiementErreur = '';
    this.cdr.detectChanges();

    setTimeout(() => {
      this.http.post<any>(
        `${this.api}/${formationId}/paiement/confirmer`,
        {
          numeroCarte: this.carteForm.numeroCarte,
          nomCarte:    this.carteForm.nomCarte,
          expiration:  this.carteForm.expiration,
          cvv:         this.carteForm.cvv
        },
        { headers: this.headers() }
      ).subscribe({
        next: res => {
          this.paiementReference = res.reference;
          this.paiementEtape     = 'succes';
          this.estInscrit        = true;
          this.cdr.detectChanges();
        },
        error: err => {
          this.paiementErreur = err.error?.message || 'Paiement refusé. Veuillez réessayer.';
          this.paiementEtape  = 'echec';
          this.cdr.detectChanges();
        }
      });
    }, 2000);
  }

  reessayer() {
    this.paiementEtape  = 'formulaire';
    this.paiementErreur = '';
    this.carteForm      = { numeroCarte: '', nomCarte: '', expiration: '', cvv: '' };
    this.cdr.detectChanges();
  }

  formatCarteNumero(event: Event) {
    const input = event.target as HTMLInputElement;
    let val = input.value.replace(/\D/g, '').substring(0, 16);
    val = val.replace(/(.{4})/g, '$1 ').trim();
    this.carteForm.numeroCarte = val;
    input.value = val;
  }

  formatExpiration(event: Event) {
    const input = event.target as HTMLInputElement;
    let val = input.value.replace(/\D/g, '').substring(0, 4);
    if (val.length >= 2) val = val.substring(0, 2) + '/' + val.substring(2);
    this.carteForm.expiration = val;
    input.value = val;
  }

  getCarteType(): 'visa' | 'mastercard' | 'unknown' {
    const n = this.carteForm.numeroCarte.replace(/\s/g, '');
    if (n.startsWith('4')) return 'visa';
    if (n.startsWith('5') || n.startsWith('2')) return 'mastercard';
    return 'unknown';
  }

  get paiementFormValide(): boolean {
    const n = this.carteForm.numeroCarte.replace(/\s/g, '');
    return n.length === 16 &&
           this.carteForm.nomCarte.trim().length > 0 &&
           /^\d{2}\/\d{2}$/.test(this.carteForm.expiration) &&
           this.carteForm.cvv.length >= 3;
  }

  // ── HELPERS ───────────────────────────────────────────────

  retourCatalogue() {
    this.router.navigate(['/home']);
  }

  getNiveauLabel(n: string): string {
    const m: any = { DEBUTANT: 'Débutant', INTERMEDIAIRE: 'Intermédiaire', AVANCE: 'Avancé' };
    return m[n] || n || '—';
  }

  getNiveauColor(n: string): string {
    const m: any = { DEBUTANT: '#27ae60', INTERMEDIAIRE: '#e67e22', AVANCE: '#8B3A3A' };
    return m[n] || '#6B5F52';
  }

  getNiveauBg(n: string): string {
    const m: any = { DEBUTANT: '#e8f5e9', INTERMEDIAIRE: '#fff3cd', AVANCE: '#fce4e4' };
    return m[n] || '#F5F1EB';
  }

  getFormateurNom(): string {
    if (this.formation?.formateurPrenom || this.formation?.formateurNom) {
      return [this.formation.formateurPrenom, this.formation.formateurNom].filter(Boolean).join(' ');
    }
    return 'Formateur à définir';
  }

  getFormateurInitiales(): string {
    const p = this.formation?.formateurPrenom?.[0] || '';
    const n = this.formation?.formateurNom?.[0]    || '';
    return (p + n).toUpperCase() || 'F';
  }

  getCoverStyle(): { [k: string]: string } {
    if (this.formation?.imageCouverture) {
      return {
        'background-image':    `url(${this.formation.imageCouverture})`,
        'background-size':     'cover',
        'background-position': 'center'
      };
    }
    return { 'background': 'linear-gradient(135deg, #8B3A3A20 0%, #8B3A3A50 100%)' };
  }
}