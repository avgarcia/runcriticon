import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, map, tap } from 'rxjs';
// OJO: el servicio generado del contrato se llama `GruposService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que `taxonomy.service.ts`.
import { GruposService as GroupsApi } from '../api/generated/services/grupos.service';
import { GroupMembersResponse } from '../api/generated/models/group-members-response';
import { GroupResponse } from '../api/generated/models/group-response';
import { GroupSummaryResponse } from '../api/generated/models/group-summary-response';

/** Grupos del club tal y como los devuelve el backend (alias de los modelos generados). */
export type GroupSummary = GroupSummaryResponse;
export type GroupMembers = GroupMembersResponse;
export type Group = GroupResponse;

/**
 * Estado de los grupos del club en la SPA. Delega el HTTP en el cliente generado desde el contrato.
 *
 * Solo el listado se guarda en el signal. La previsualización de miembros no: es estado efímero de
 * quien está construyendo un filtro, muere con la pantalla y compartirlo entre pantallas solo daría
 * ocasión de enseñar un número viejo.
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

  /**
   * Alumnos que cumplirían este filtro sin guardar nada. Sin filtro devuelve cero alumnos, no el club
   * entero: esa semántica la fija el backend y repetirla aquí la dejaría definida en dos sitios.
   */
  previewMembers(valores: readonly string[]): Observable<GroupMembers> {
    return from(this.api.previsualizarMiembrosDeGrupo({ tagValueId: [...valores] }));
  }

  /**
   * Crea el grupo e **invalida** la caché en vez de parchearla: el alta devuelve el grupo pero no
   * cuántos alumnos caen dentro, y colocar un cero de relleno pintaría un número falso en la lista.
   * Quien la necesite la vuelve a cargar.
   */
  create(nombre: string, valores: readonly string[]): Observable<Group> {
    return from(this.api.crearGrupo({ body: { nombre, valores: [...valores] } })).pipe(
      tap(() => this.currentGroups.set(undefined)),
    );
  }

  /** Vacía la caché (al cerrar sesión): otro usuario puede pertenecer a otro club. */
  reset(): void {
    this.currentGroups.set(undefined);
  }
}
