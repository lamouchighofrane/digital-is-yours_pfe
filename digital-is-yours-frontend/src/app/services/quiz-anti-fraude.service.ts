import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';

// ── Types exportés ─────────────────────────────────────────────────

export type TypeInfraction =
  | 'onglet_quitte'
  | 'copie'
  | 'raccourci'
  | 'plein_ecran';

export type NiveauFraude = 'vert' | 'orange' | 'rouge';

export interface Infraction {
  type:       TypeInfraction;
  message:    string;         // Message exact affiché à l'apprenant
  horodatage: string;         // ISO-8601
}

export interface EtatFraude {
  niveau:          NiveauFraude;
  nbInfractions:   number;
  derniereInfraction: Infraction | null;
  infractions:     Infraction[];
}

export interface RapportFraude {
  nombreInfractions: number;
  infractions:       Infraction[];
}

// ── Service ────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class QuizAntiFraudeService implements OnDestroy {

  // Observable que le composant écoute pour réagir en temps réel
  private etatSubject = new Subject<EtatFraude>();
  etat$ = this.etatSubject.asObservable();

  // État interne
  private actif        = false;
  private infractions: Infraction[] = [];

  // Listeners à désinscrire
  private handlers: { event: string; fn: EventListener; target: EventTarget }[] = [];

  constructor(private zone: NgZone) {}

  // ════════════════════════════════════════════════════════════════
  // DÉMARRER
  // ════════════════════════════════════════════════════════════════

  demarrer(): void {
    if (this.actif) return;
    this.actif = true;
    this.infractions = [];

    this.zone.runOutsideAngular(() => {

      // 1. Changement d'onglet
      this.on(document, 'visibilitychange', () => {
        if (document.hidden) this.enregistrer('onglet_quitte', this.messageOnglet());
      });

      // 2. Perte de focus fenêtre (alt-tab)
      this.on(window, 'blur', () => {
        this.enregistrer('onglet_quitte', this.messageOnglet());
      });

      // 3. Copier / Couper
      this.on(document, 'copy', (e) => {
        e.preventDefault();
        this.enregistrer('copie', this.messageCopie());
      });
      this.on(document, 'cut', (e) => {
        e.preventDefault();
        this.enregistrer('copie', this.messageCopie());
      });

      // 4. Clic droit (contextmenu)
      this.on(document, 'contextmenu', (e) => {
        e.preventDefault();
        this.enregistrer('copie', 'Clic droit bloqué. Cette action a été enregistrée.');
      });

      // 5. Raccourcis suspects
      this.on(document, 'keydown', (e) => {
        const evt = e as KeyboardEvent;
        const msg = this.detecterRaccourci(evt);
        if (msg) {
          evt.preventDefault();
          this.enregistrer('raccourci', msg);
        }
      });

      // 6. Sortie plein écran
      this.on(document, 'fullscreenchange', () => {
        if (!document.fullscreenElement) {
          this.enregistrer('plein_ecran', this.messagePleinEcran());
        }
      });
    });
  }

  // ════════════════════════════════════════════════════════════════
  // ARRÊTER
  // ════════════════════════════════════════════════════════════════

  arreter(): void {
    if (!this.actif) return;
    this.actif = false;
    this.handlers.forEach(({ event, fn, target }) => target.removeEventListener(event, fn));
    this.handlers = [];
  }

  // ════════════════════════════════════════════════════════════════
  // PLEIN ÉCRAN
  // ════════════════════════════════════════════════════════════════

  demanderPleinEcran(element: HTMLElement): void {
    if (element.requestFullscreen) {
      element.requestFullscreen().catch(() => {});
    }
  }

  // ════════════════════════════════════════════════════════════════
  // RAPPORT FINAL (envoyé avec la soumission)
  // ════════════════════════════════════════════════════════════════

  getRapport(): RapportFraude {
    return {
      nombreInfractions: this.infractions.length,
      infractions:       [...this.infractions],
    };
  }

  getNiveauActuel(): NiveauFraude {
    const n = this.infractions.length;
    if (n === 0) return 'vert';
    if (n <= 2)  return 'orange';
    return 'rouge';
  }

  getNbInfractions(): number {
    return this.infractions.length;
  }

  getInfractions(): Infraction[] {
    return [...this.infractions];
  }

  // ════════════════════════════════════════════════════════════════
  // CORE PRIVÉ
  // ════════════════════════════════════════════════════════════════

  private enregistrer(type: TypeInfraction, message: string): void {
    const infraction: Infraction = {
      type,
      message,
      horodatage: new Date().toISOString(),
    };
    this.infractions.push(infraction);

    const etat: EtatFraude = {
      niveau:             this.getNiveauActuel(),
      nbInfractions:      this.infractions.length,
      derniereInfraction: infraction,
      infractions:        [...this.infractions],
    };

    this.zone.run(() => this.etatSubject.next(etat));
  }

  private on(target: EventTarget, event: string, fn: EventListener): void {
    target.addEventListener(event, fn, { passive: false } as any);
    this.handlers.push({ event, fn, target });
  }

  // ════════════════════════════════════════════════════════════════
  // MESSAGES EXACTS PAR TYPE
  // ════════════════════════════════════════════════════════════════

  private messageOnglet(): string {
    const nb = this.infractions.filter(i => i.type === 'onglet_quitte').length + 1;
    if (nb === 1) {
      return 'Vous avez quitté l\'onglet du quiz. Cette action a été enregistrée.';
    }
    return `Vous avez quitté l'onglet du quiz ${nb} fois. Chaque sortie est transmise au formateur.`;
  }

  private messageCopie(): string {
    const nb = this.infractions.filter(i => i.type === 'copie').length + 1;
    if (nb === 1) {
      return 'Tentative de copie du contenu bloquée. Cette action a été enregistrée.';
    }
    return `Tentative de copie bloquée (${nb}ème fois). Cette action a été signalée au formateur.`;
  }

  private messagePleinEcran(): string {
    const nb = this.infractions.filter(i => i.type === 'plein_ecran').length + 1;
    if (nb === 1) {
      return 'Vous avez quitté le mode plein écran. Cette action a été enregistrée.';
    }
    return `Vous avez quitté le mode plein écran ${nb} fois. Cette action a été signalée.`;
  }

  private detecterRaccourci(e: KeyboardEvent): string | null {
    // F12
    if (e.key === 'F12') {
      return 'Tentative d\'ouverture des outils développeur (F12) bloquée. Cette action a été enregistrée.';
    }
    // Ctrl+Shift+I — DevTools
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'I') {
      return 'Tentative d\'ouverture des outils développeur (Ctrl+Shift+I) bloquée. Cette action a été enregistrée.';
    }
    // Ctrl+Shift+J — Console
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'J') {
      return 'Tentative d\'ouverture de la console (Ctrl+Shift+J) bloquée. Cette action a été enregistrée.';
    }
    // Ctrl+U — Voir source
    if ((e.ctrlKey || e.metaKey) && e.key === 'u') {
      return 'Tentative d\'affichage du code source (Ctrl+U) bloquée. Cette action a été enregistrée.';
    }
    // Ctrl+P — Imprimer
    if ((e.ctrlKey || e.metaKey) && e.key === 'p') {
      return 'Tentative d\'impression (Ctrl+P) bloquée. Cette action a été enregistrée.';
    }
    // Ctrl+C — Copier
    if ((e.ctrlKey || e.metaKey) && e.key === 'c') {
      return 'Tentative de copie (Ctrl+C) bloquée. Cette action a été enregistrée.';
    }
    return null;
  }

  ngOnDestroy(): void { this.arreter(); }
}
