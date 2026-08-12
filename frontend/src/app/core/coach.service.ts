import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, map, tap } from 'rxjs';
// OJO: el servicio generado del contrato se llama `EntrenadoresService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que `group.service.ts`.
import { EntrenadoresService as CoachesApi } from '../api/generated/services/entrenadores.service';
import { CoachWorkloadResponse } from '../api/generated/models/coach-workload-response';

/** Un entrenador del club con su carga, tal y como lo devuelve el backend (alias del modelo generado). */
export type CoachWorkload = CoachWorkloadResponse;

/**
 * Estado de los entrenadores del club en la SPA. Delega el HTTP en el cliente generado desde el contrato.
 *
 * No confundir con la gestión de sesión de entrenadores de `features/identidad/pages/coaches.component.ts`
 * (`/api/entrenadores`, revocar/desactivar): este servicio consume `/api/entrenadores/resumen`, la vista
 * de club sobre la proyección local, con la carga de grupos.
 */
@Injectable({ providedIn: 'root' })
export class CoachService {
  private readonly api = inject(CoachesApi);

  private readonly currentCoaches = signal<CoachWorkload[] | undefined>(undefined);

  /** Entrenadores del club (solo lectura). `undefined` mientras no se hayan cargado. */
  readonly coaches = this.currentCoaches.asReadonly();

  /** Carga los entrenadores del club con su carga de grupos. */
  load(): Observable<CoachWorkload[]> {
    return from(this.api.listarResumenDeEntrenadores()).pipe(
      map((response) => response.entrenadores),
      tap((entrenadores) => this.currentCoaches.set(entrenadores)),
    );
  }

  /** Vacía la caché (al cerrar sesión): otro usuario puede pertenecer a otro club. */
  reset(): void {
    this.currentCoaches.set(undefined);
  }
}
