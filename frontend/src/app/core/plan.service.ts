import { Injectable, inject } from '@angular/core';
import { Observable, from, map } from 'rxjs';
// OJO: el servicio generado del contrato se llama `PlanesService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que `group.service.ts`.
import { PlanesService as PlansApi } from '../api/generated/services/planes.service';
import { PlanDetalleResponse } from '../api/generated/models/plan-detalle-response';
import { PlanResponse } from '../api/generated/models/plan-response';
import { PublicacionResponse } from '../api/generated/models/publicacion-response';
import { TrainingSessionRequest } from '../api/generated/models/training-session-request';
import { TrainingSessionResponse } from '../api/generated/models/training-session-response';
import { TrainingSessionUpdateRequest } from '../api/generated/models/training-session-update-request';

/** Un plan semanal en borrador, tal y como lo devuelve el backend (alias del modelo generado). */
export type Plan = PlanResponse;

/** El plan completo con sus sesiones (LAL-24, alias del modelo generado). */
export type PlanDetail = PlanDetalleResponse;

/** El plan tras publicarse, con el tamaño del snapshot congelado (LAL-25, alias del modelo generado). */
export type PublicationResult = PublicacionResponse;

/** Una sesión de entrenamiento dentro de un plan. Alias `PlanSession`, no `Session`, para no
 * confundirla con la sesión de login que gestiona `session.service.ts`. */
export type PlanSession = TrainingSessionResponse;

/** Datos de alta/edición de una sesión — reexporta los modelos generados tal cual. */
export type CreateSessionData = TrainingSessionRequest;
export type UpdateSessionData = TrainingSessionUpdateRequest;

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

  /** El plan completo, con sus sesiones. */
  get(planId: string): Observable<PlanDetail> {
    return from(this.api.consultarPlan({ planId }));
  }

  /** Añade una sesión al plan. `dia` debe caer dentro de la semana del plan, o el backend lo rechaza. */
  addSession(planId: string, body: CreateSessionData): Observable<PlanSession> {
    return from(this.api.anadirSesion({ planId, body }));
  }

  /** Edita tipo, volumen, ritmo y notas de una sesión existente — sin día, que no se puede mover. */
  updateSession(planId: string, sesionId: string, body: UpdateSessionData): Observable<PlanSession> {
    return from(this.api.editarSesion({ planId, sesionId, body }));
  }

  /** Elimina una sesión del plan. */
  deleteSession(planId: string, sesionId: string): Observable<void> {
    return from(this.api.eliminarSesion({ planId, sesionId }));
  }

  /** Publica el plan al grupo (LAL-25): congela el snapshot de alumnos y deja el plan en `PUBLICADO`. */
  publish(planId: string): Observable<PublicationResult> {
    return from(this.api.publicarPlan({ planId }));
  }
}
