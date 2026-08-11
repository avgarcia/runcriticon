import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, map, tap } from 'rxjs';
// OJO: el servicio generado del contrato se llama `AlumnosService`. Se importa aliasado para que el
// servicio de estado de la app conserve el nombre en inglés, igual que `group.service.ts`.
import { AlumnosService as StudentsApi } from '../api/generated/services/alumnos.service';
import { StudentSummaryResponse } from '../api/generated/models/student-summary-response';

/** Un alumno del club tal y como lo devuelve el backend (alias del modelo generado). */
export type StudentSummary = StudentSummaryResponse;

/**
 * Estado de los alumnos del club en la SPA. Delega el HTTP en el cliente generado desde el contrato.
 *
 * Solo el listado se guarda en el signal. El alta no parchea la caché: el alta no devuelve el
 * listado completo con sus tags, así que quien invita a un alumno vuelve a pedir la lista.
 */
@Injectable({ providedIn: 'root' })
export class StudentService {
  private readonly api = inject(StudentsApi);

  private readonly currentStudents = signal<StudentSummary[] | undefined>(undefined);

  /** Alumnos del club (solo lectura). `undefined` mientras no se hayan cargado. */
  readonly students = this.currentStudents.asReadonly();

  /**
   * Carga los alumnos del club. Sin `tagValueIds`, o con la lista vacía, trae a todos — la ausencia
   * de filtro no es un filtro vacío, la fija así el propio contrato.
   */
  load(tagValueIds: readonly string[] = []): Observable<StudentSummary[]> {
    return from(this.api.listarAlumnos({ tagValueId: [...tagValueIds] })).pipe(
      map((response) => response.alumnos),
      tap((alumnos) => this.currentStudents.set(alumnos)),
    );
  }

  /** Vacía la caché (al cerrar sesión): otro usuario puede pertenecer a otro club. */
  reset(): void {
    this.currentStudents.set(undefined);
  }
}
