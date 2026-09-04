import { Injectable, inject } from '@angular/core';
import { Observable, from } from 'rxjs';
import { AlertasService as CoachAlertApi } from '../api/generated/services/alertas.service';
import { CoachAlert } from '../api/generated/models/coach-alert';

/** Una alerta activa del panel del entrenador (LAL-116, alias del modelo generado). */
export type Alert = CoachAlert;

/**
 * Alertas activas del entrenador. Sin signal cacheado, mismo criterio que `MyPlanService`: es un panel de
 * solo lectura sin acciones que invaliden la lista, así que cada carga es una llamada nueva.
 */
@Injectable({ providedIn: 'root' })
export class CoachAlertService {
  private readonly api = inject(CoachAlertApi);

  /** Alertas activas de los grupos del entrenador, o solo de [grupoId] si se indica. */
  getAlerts(grupoId?: string): Observable<Alert[]> {
    return from(this.api.listarAlertasEntrenador({ grupoId }).then((response) => response.alertas));
  }
}
