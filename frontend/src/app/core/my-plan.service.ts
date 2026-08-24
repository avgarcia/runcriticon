import { Injectable, inject } from '@angular/core';
import { Observable, from } from 'rxjs';
// OJO: el servicio generado del contrato se llama `SeguimientoService`. Se importa aliasado para
// no confundirlo con `session.service.ts`/`SessionService`, mismo criterio que `plan.service.ts`
// con `PlanesService`.
import { SeguimientoService as MyPlanApi } from '../api/generated/services/seguimiento.service';
import { MiPlanSemanalResponse } from '../api/generated/models/mi-plan-semanal-response';
import { MiResolvedSessionResponse } from '../api/generated/models/mi-resolved-session-response';

/** La semana del propio alumno, ya resuelta (LAL-29, alias del modelo generado). */
export type MyWeek = MiPlanSemanalResponse;

/** Una sesión de la semana, ya resuelta para el alumno (alias del modelo generado). */
export type MyResolvedSession = MiResolvedSessionResponse;

/**
 * La semana resuelta del propio alumno. Sin signal de estado propio, mismo criterio que
 * `PlanService`: la pantalla que lo usa siempre pide una semana concreta, y cachear por semana no
 * aporta nada que una recarga no dé ya.
 */
@Injectable({ providedIn: 'root' })
export class MyPlanService {
  private readonly api = inject(MyPlanApi);

  /** La semana pedida (lunes en `YYYY-MM-DD`) o, si se omite, la semana en curso según el backend. */
  getWeek(semana?: string): Observable<MyWeek> {
    return from(this.api.consultarMiSemana({ semana }));
  }
}
