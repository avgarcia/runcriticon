import { Injectable, inject } from '@angular/core';
import { Observable, from } from 'rxjs';
import { SaludDelClubService as ClubHealthApi } from '../api/generated/services/salud-del-club.service';
import { ActividadDeGrupoResponse } from '../api/generated/models/actividad-de-grupo-response';

/** La última actividad reportada de un grupo (alias del modelo generado). */
export type GroupActivity = ActividadDeGrupoResponse;

/**
 * Última actividad por grupo para la vista de salud del club (admin). Sin signal cacheado, mismo
 * criterio que `CoachAlertService`: es un panel de solo lectura sin acciones que invaliden la lista.
 */
@Injectable({ providedIn: 'root' })
export class ClubHealthService {
  private readonly api = inject(ClubHealthApi);

  /** Un elemento por cada grupo con al menos un reporte; un grupo sin ninguno no aparece. */
  getGroupActivity(): Observable<GroupActivity[]> {
    return from(this.api.consultarActividadPorGrupo().then((response) => response.grupos));
  }
}
