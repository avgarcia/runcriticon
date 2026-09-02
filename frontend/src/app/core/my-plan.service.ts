import { Injectable, inject } from '@angular/core';
import { Observable, from, map } from 'rxjs';
// OJO: el servicio generado del contrato se llama `SeguimientoService`. Se importa aliasado para
// no confundirlo con `session.service.ts`/`SessionService`, mismo criterio que `plan.service.ts`
// con `PlanesService`.
import { SeguimientoService as MyPlanApi } from '../api/generated/services/seguimiento.service';
import { MiPlanSemanalResponse } from '../api/generated/models/mi-plan-semanal-response';
import { MiReajusteRequest } from '../api/generated/models/mi-reajuste-request';
import { MiReajusteResponse } from '../api/generated/models/mi-reajuste-response';
import { MiReporteRequest } from '../api/generated/models/mi-reporte-request';
import { MiResolvedSessionResponse } from '../api/generated/models/mi-resolved-session-response';

/** La semana del propio alumno, ya resuelta (LAL-29, alias del modelo generado). */
export type MyWeek = MiPlanSemanalResponse;

/** Una sesión de la semana, ya resuelta para el alumno (alias del modelo generado). */
export type MyResolvedSession = MiResolvedSessionResponse;

/** Lo que el alumno envía al reportar una sesión (LAL-30, alias del modelo generado). */
export type SessionReportData = MiReporteRequest;

/** Lo que el alumno envía al reajustar el día de una sesión (LAL-33, alias del modelo generado). */
export type RescheduleData = MiReajusteRequest;

/** El reajuste ya guardado de una sesión (LAL-33, alias del modelo generado). */
export type RescheduleResult = MiReajusteResponse;

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

  /** Envío idempotente (LAL-30): la primera vez crea el reporte del día, las siguientes lo
   * reemplaza. Devuelve la sesión del día ya con el reporte aplicado. */
  submitReport(dia: string, body: SessionReportData): Observable<MyResolvedSession> {
    return from(this.api.reportarSesion({ dia, body }));
  }

  /** Envío idempotente (LAL-33): la primera vez crea el reajuste del día, las siguientes lo
   * reemplaza. [dia] es el día EFECTIVO de la sesión de origen. */
  rescheduleDay(dia: string, body: RescheduleData): Observable<RescheduleResult> {
    return from(this.api.reajustarDia({ dia, body }));
  }

  /** Idempotente (LAL-33): deshace el reajuste de [dia], con o sin reajuste previo. */
  withdrawAdjustment(dia: string): Observable<void> {
    return from(this.api.deshacerReajuste({ dia })).pipe(map(() => undefined));
  }
}
