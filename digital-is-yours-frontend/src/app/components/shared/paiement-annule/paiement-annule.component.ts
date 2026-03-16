import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-paiement-annule',
  templateUrl: './paiement-annule.component.html',
  styleUrls: ['./paiement-annule.component.css']
})
export class PaiementAnnuleComponent implements OnInit {
  formationId: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.formationId = this.route.snapshot.queryParamMap.get('formation_id');
  }

  retourFormation(): void {
    if (this.formationId) {
      this.router.navigate(['/formation', this.formationId]);
    } else {
      this.router.navigate(['/home']);
    }
  }
}