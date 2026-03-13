import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Formation {
  id: number;
  titre: string;
  description: string;
  imageCouverture: string;
  niveau: string;
  dureeEstimee: number;
  statut: string;
  formateurPrenom: string;
  formateurNom: string;
  categorieId: number;
}

export interface Categorie {
  id: number;
  nom: string;
  description: string;
  couleur: string;
  imageCouverture: string;
  ordreAffichage: number;
  visibleCatalogue: boolean;
}

@Injectable({ providedIn: 'root' })
export class FormationService {
  private publicUrl = 'http://localhost:8080/api/public';

  constructor(private http: HttpClient) {}

  getFormationsPubliees(): Observable<Formation[]> {
    return this.http.get<Formation[]>(`${this.publicUrl}/formations`);
  }

  getCategoriesVisibles(): Observable<Categorie[]> {
    return this.http.get<Categorie[]>(`${this.publicUrl}/categories`);
  }
}