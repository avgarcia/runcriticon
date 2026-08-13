import { Injectable, inject } from '@angular/core';
import { Observable, from, map } from 'rxjs';
// OJO: el servicio generado del contrato se llama `PlanesService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que `group.service.ts`.
import { PlanesService as PlansApi } from '../api/generated/services/planes.service';
import { PlanResponse } from '../api/generated/models/plan-response';

/** Un plan semanal en borrador, tal y como lo devuelve el backend (alias del modelo generado). */
export type Plan = PlanResponse;

/**
 * Planes en borrador de un grupo. Sin signal de estado propio, a diferencia de `GroupService`/`CoachService`:
 * la pantalla que lo usa siempre pide los planes de un grupo concreto, y cachear por grupo no aporta nada que
 * una recarga no dé ya.
 */
@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly api = inject(PlansApi);

  /** Planes en borrador de [grupoId]. Vacío si el grupo no existe o el entrenador no tiene relación con él. */
  listDrafts(grupoId: string): Observable<Plan[]> {
    return from(this.api.listarPlanesEnBorrador({ grupoId })).pipe(map((response) => response.planes));
  }

  /** Crea el plan en borrador. `semana` debe ser el lunes de esa semana (YYYY-MM-DD), o el backend lo rechaza. */
  create(grupoId: string, semana: string): Observable<Plan> {
    return from(this.api.crearPlanEnBorrador({ body: { grupoId, semana } }));
  }
}
