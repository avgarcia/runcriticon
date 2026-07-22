import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, tap } from 'rxjs';
import { MeService } from '../api/generated/services/me.service';
import { PermissionsResponse } from '../api/generated/models/permissions-response';

/**
 * Permisos del principal actual, cacheados para ocultar acciones que su rol no puede ejecutar.
 *
 * REGLA DE ORO: esto es **ayuda de UX, nunca una barrera**. Ocultar un botón no impide
 * nada — el backend autoriza cada petición por su cuenta, y las rutas siguen protegidas por sus
 * guards. Si este servicio se equivoca por exceso, no se abre ningún agujero.
 *
 * Es fail-closed: mientras no haya respuesta, o si la carga falla, `can()` devuelve `false`.
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly api = inject(MeService);

  private readonly granted = signal<PermissionsResponse | null>(null);

  /** Mapa `{ recurso: [acciones] }` concedido al principal; `null` mientras no se haya cargado. */
  readonly permissions = this.granted.asReadonly();

  load(): Observable<PermissionsResponse> {
    return from(this.api.consultarMisPermisos()).pipe(
      tap({
        next: (permissions) => this.granted.set(permissions),
        error: () => this.granted.set(null),
      }),
    );
  }

  /** Carga los permisos solo si no están ya en memoria. Lo llama el shell al montarse. */
  loadOnce(): void {
    if (this.granted() !== null) {
      return;
    }
    this.load().subscribe({ error: () => undefined });
  }

  /**
   * ¿El principal tiene concedida esta acción sobre este recurso? Lectura de signal, sin HTTP:
   * es barato llamarlo varias veces desde una plantilla.
   */
  can(resource: string, action: string): boolean {
    return this.granted()?.[resource]?.includes(action) ?? false;
  }

  /** Vacía la caché (al cerrar sesión): el siguiente usuario puede tener otro rol. */
  reset(): void {
    this.granted.set(null);
  }
}
