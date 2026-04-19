import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';

export type StatutCamera =
  | 'inactive'
  | 'demande'
  | 'active'
  | 'refusee'
  | 'orange'
  | 'erreur';

export interface EvenementCamera {
  type:       'absence_visage' | 'visages_multiples' | 'camera_refusee';
  message:    string;
  horodatage: string;
  nbVisages:  number;
}

export interface EtatCamera {
  statut:             StatutCamera;
  nbVisagesDetectes:  number;
  compteurAbsence:    number;
  compteurMultiple:   number;
  derniereInfraction: EvenementCamera | null;
}

@Injectable({ providedIn: 'root' })
export class QuizCameraService implements OnDestroy {

  private etatSubject  = new Subject<EtatCamera>();
  private infraSubject = new Subject<EvenementCamera>();
  etat$  = this.etatSubject.asObservable();
  infra$ = this.infraSubject.asObservable();

  private actif              = false;
  private stream:              MediaStream | null = null;
  private videoEl:             HTMLVideoElement | null = null;
  private analyseInterval:     any = null;
  private compteurAbsence      = 0;
  private compteurMultiple     = 0;
  private statut:              StatutCamera = 'inactive';
  private infractions:         EvenementCamera[] = [];
  private cameraRefusee        = false;
  private faceApiCharge        = false;

  // ── SEUILS ─────────────────────────────────────────────────────
  readonly INTERVALLE_MS    = 3000;  // analyse toutes les 3s
  readonly SEUIL_ORANGE     = 2;     // orange après 6s (2 × 3s)
  readonly SEUIL_INFRACTION = 4;     // infraction après 12s (4 × 3s)

  private readonly MODELS_URL = '/assets/face-models';

  constructor(private zone: NgZone) {}

  // ════════════════════════════════════════════════════════════════
  // DÉMARRER
  // ════════════════════════════════════════════════════════════════

  async demarrer(videoElement: HTMLVideoElement): Promise<void> {
    await this.arreterAsync();

    this.actif   = true;
    this.videoEl = videoElement;
    this.reinitialiser();
    this.majStatut('demande');

    try {
      await this.chargerFaceApi();

      this.stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width:      { ideal: 320 },
          height:     { ideal: 240 },
          facingMode: 'user'
        }
      });

      this.videoEl.srcObject = this.stream;
      await this.videoEl.play();
      this.majStatut('active');

      // Attendre que la vidéo soit prête
      await new Promise<void>(resolve => {
        const check = setInterval(() => {
          if (this.videoEl && this.videoEl.readyState >= 2) {
            clearInterval(check);
            resolve();
          }
        }, 100);
      });

      // Première analyse immédiate pour confirmer la détection
      await this.analyser();

      this.zone.runOutsideAngular(() => {
        this.analyseInterval = setInterval(
          () => this.analyser(),
          this.INTERVALLE_MS
        );
      });

    } catch (err: any) {
      if (
        err.name === 'NotAllowedError'       ||
        err.name === 'PermissionDeniedError'  ||
        err.name === 'SecurityError'
      ) {
        this.cameraRefusee = true;
        this.majStatut('refusee');
        this.enregistrerInfraction({
          type:       'camera_refusee',
          message:    'Accès à la caméra refusé. La surveillance vidéo est désactivée.',
          horodatage: new Date().toISOString(),
          nbVisages:  0,
        });
      } else {
        this.majStatut('erreur');
        console.error('Erreur caméra:', err);
      }
    }
  }

  // ════════════════════════════════════════════════════════════════
  // ARRÊTER
  // ════════════════════════════════════════════════════════════════

  arreter(): void {
    this.actif = false;
    clearInterval(this.analyseInterval);
    this.analyseInterval = null;

    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
      this.stream = null;
    }
    if (this.videoEl) {
      this.videoEl.srcObject = null;
      this.videoEl = null;
    }
    this.majStatut('inactive');
  }

  private async arreterAsync(): Promise<void> {
    this.arreter();
    await new Promise(resolve => setTimeout(resolve, 200));
  }

  // ════════════════════════════════════════════════════════════════
  // RAPPORT
  // ════════════════════════════════════════════════════════════════

  getInfractions(): EvenementCamera[] {
    return [...this.infractions];
  }

  estCameraRefusee(): boolean {
    return this.cameraRefusee;
  }

  getNbInfractions(): number {
    return this.infractions.filter(
      i => i.type === 'absence_visage' || i.type === 'visages_multiples'
    ).length;
  }

  // ════════════════════════════════════════════════════════════════
  // ANALYSE — PARAMÈTRES CORRIGÉS POUR WEBCAM STANDARD
  // ════════════════════════════════════════════════════════════════

  private async analyser(): Promise<void> {
    if (!this.videoEl || !this.faceApiCharge || !this.actif) return;
    if (this.videoEl.readyState < 2) return;

    // Vérifier que la vidéo a bien des dimensions (pas une image noire)
    if (this.videoEl.videoWidth === 0 || this.videoEl.videoHeight === 0) return;

    const faceapi = (window as any).faceapi;
    if (!faceapi) return;

    try {
      const detections = await faceapi.detectAllFaces(
        this.videoEl,
        new faceapi.TinyFaceDetectorOptions({
          // CORRECTION PRINCIPALE :
          // 0.3 au lieu de 0.55 — webcams standard ont une qualité limitée
          // Un seuil trop élevé = aucune détection même si le visage est là
          scoreThreshold: 0.3,
          // 224 est la valeur OPTIMALE pour TinyFaceDetector
          // 416 est trop lourd et cause des délais de détection
          inputSize: 224
        })
      );

      const nbVisages = detections.length;

      this.zone.run(() => {
        if (nbVisages === 0) {
          this.traiterAbsence(nbVisages);
        } else if (nbVisages >= 2) {
          this.traiterMultiple(nbVisages);
        } else {
          // 1 visage — situation normale
          this.compteurAbsence  = 0;
          this.compteurMultiple = 0;
          this.majStatut('active');
          this.emettreEtat(nbVisages);
        }
      });

    } catch (err) {
      console.warn('Erreur analyse face-api:', err);
    }
  }

  // ════════════════════════════════════════════════════════════════
  // TRAITEMENT ABSENCE
  // ════════════════════════════════════════════════════════════════

  private traiterAbsence(nbVisages: number): void {
    this.compteurMultiple = 0;
    this.compteurAbsence++;

    if (this.compteurAbsence < this.SEUIL_ORANGE) {
      this.majStatut('active');             // moins de 6s → rien
    } else if (this.compteurAbsence < this.SEUIL_INFRACTION) {
      this.majStatut('orange');             // 6–12s → orange discret
    } else {
      if (this.compteurAbsence === this.SEUIL_INFRACTION) {
        const duree = Math.round(this.compteurAbsence * this.INTERVALLE_MS / 1000);
        this.enregistrerInfraction({
          type:       'absence_visage',
          message:    `Aucun visage détecté pendant ${duree} secondes. Cette absence a été enregistrée.`,
          horodatage: new Date().toISOString(),
          nbVisages:  0,
        });
      }
      this.majStatut('orange');
    }

    this.emettreEtat(nbVisages);
  }

  // ════════════════════════════════════════════════════════════════
  // TRAITEMENT VISAGES MULTIPLES
  // ════════════════════════════════════════════════════════════════

  private traiterMultiple(nbVisages: number): void {
    this.compteurAbsence = 0;
    this.compteurMultiple++;

    if (this.compteurMultiple < this.SEUIL_ORANGE) {
      this.majStatut('active');
    } else if (this.compteurMultiple < this.SEUIL_INFRACTION) {
      this.majStatut('orange');
    } else {
      if (this.compteurMultiple === this.SEUIL_INFRACTION) {
        this.enregistrerInfraction({
          type:       'visages_multiples',
          message:    `${nbVisages} visages détectés devant la caméra. Cette situation a été enregistrée.`,
          horodatage: new Date().toISOString(),
          nbVisages,
        });
      }
      this.majStatut('orange');
    }

    this.emettreEtat(nbVisages);
  }

  // ════════════════════════════════════════════════════════════════
  // CHARGEMENT face-api.js
  // ════════════════════════════════════════════════════════════════

  private async chargerFaceApi(): Promise<void> {
    if (this.faceApiCharge) return;

    if (!(window as any).faceapi) {
      await this.chargerScript(
        'https://cdn.jsdelivr.net/npm/face-api.js@0.22.2/dist/face-api.min.js'
      );
    }

    const faceapi = (window as any).faceapi;
    await faceapi.nets.tinyFaceDetector.loadFromUri(this.MODELS_URL);
    this.faceApiCharge = true;
  }

  private chargerScript(src: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const existing = document.querySelector(`script[src="${src}"]`);
      if (existing) { resolve(); return; }
      const script    = document.createElement('script');
      script.src      = src;
      script.onload   = () => resolve();
      script.onerror  = () => reject(new Error(`Impossible de charger ${src}`));
      document.head.appendChild(script);
    });
  }

  // ════════════════════════════════════════════════════════════════
  // HELPERS
  // ════════════════════════════════════════════════════════════════

  private enregistrerInfraction(evt: EvenementCamera): void {
    this.infractions.push(evt);
    this.zone.run(() => this.infraSubject.next(evt));
  }

  private majStatut(statut: StatutCamera): void {
    this.statut = statut;
  }

  private emettreEtat(nbVisages: number): void {
    this.etatSubject.next({
      statut:             this.statut,
      nbVisagesDetectes:  nbVisages,
      compteurAbsence:    this.compteurAbsence,
      compteurMultiple:   this.compteurMultiple,
      derniereInfraction: this.infractions.length > 0
        ? this.infractions[this.infractions.length - 1]
        : null,
    });
  }

  private reinitialiser(): void {
    this.infractions      = [];
    this.compteurAbsence  = 0;
    this.compteurMultiple = 0;
    this.cameraRefusee    = false;
    this.statut           = 'inactive';
  }

  ngOnDestroy(): void { this.arreter(); }
}
