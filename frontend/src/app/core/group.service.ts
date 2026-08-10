import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, map, tap } from 'rxjs';
// OJO: el servicio generado del contrato se llama `GruposService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que `taxonomy.service.ts`.
import { GruposService as GroupsApi } from '../api/generated/services/grupos.service';
import { GroupSummaryResponse } from '../api/generated/models/group-summary-response';

/** Grupos del club tal y como los devuelve el backend (alias de los modelos generados). */
export type GroupSummary = GroupSummaryResponse;

/**
 * Estado de los grupos del club en la SPA. Delega el HTTP en el cliente generado desde el contrato.
 */
@Injectable({ providedIn: 'root' })
export class GroupService {
  private readonly api = inject(GroupsApi);

  private readonly currentGroups = signal<GroupSummary[] | undefined>(undefined);

  /** Grupos del club (solo lectura). `undefined` mientras no se hayan cargado. */
  readonly groups = this.currentGroups.asReadonly();

  /** Carga los grupos con su recuento de alumnos, ya ordenados por el servidor. */
  load(): Observable<GroupSummary[]> {
    return from(this.api.listarGrupos()).pipe(
      map((response) => response.grupos),
      tap((grupos) => this.currentGroups.set(grupos)),
    );
  }

  /** Vacía la caché (al cerrar sesión): otro usuario puede pertenecer a otro club. */
  reset(): void {
    this.currentGroups.set(undefined);
  }
}
