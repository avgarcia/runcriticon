import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, tap } from 'rxjs';
// OJO: el servicio generado del contrato también se llama `ClubService`. Se importa aliasado a
// `ClubApi` para que el servicio de estado de la app conserve el nombre natural, igual que hace
// `session.service.ts` con `SesionService`. No deshacer el alias: la colisión rompe el build.
import { ClubService as ClubApi } from '../api/generated/services/club.service';
import { ClubResponse } from '../api/generated/models/club-response';

/** Ficha del club tal y como la devuelve el backend (alias del modelo generado del contrato). */
export type Club = ClubResponse;

/**
 * Estado de la ficha del club en la SPA (ADR-0006 D30). Delega el HTTP en el cliente generado desde
 * el contrato OpenAPI — nada de URLs a mano (frontend/CLAUDE.md).
 *
 * El estado es de tres valores a propósito: `undefined` = aún sin cargar, `null` = el backend
 * respondió 404, y un objeto = ficha cargada. El interceptor de errores deja pasar el 404 sin
 * toast, así que la UI necesita distinguir "cargando" de "no existe" para no pintar un formulario
 * vacío como si fuera un fallo de red.
 */
@Injectable({ providedIn: 'root' })
export class ClubService {
  private readonly api = inject(ClubApi);

  private readonly currentClub = signal<Club | null | undefined>(undefined);

  /** Ficha del club (solo lectura). `undefined` sin cargar, `null` si no existe. */
  readonly club = this.currentClub.asReadonly();

  /** Carga la ficha del club. En 404 deja el estado en `null` y propaga el error al llamante. */
  load(): Observable<Club> {
    return from(this.api.consultarClub()).pipe(
      tap({
        next: (club) => this.currentClub.set(club),
        // Solo el 404 significa "no existe". Cualquier otro fallo (500, sin conexión) deja el
        // estado sin cargar a propósito: así `loadOnce()` puede reintentarlo más adelante en vez de
        // dejar la pantalla clavada en el estado vacío. Del aviso al usuario ya se ocupa el
        // interceptor global con su toast.
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 404) {
            this.currentClub.set(null);
          }
        },
      }),
    );
  }

  /**
   * Carga la ficha solo si no se ha intentado antes. Lo llama el shell al montarse, de modo que la
   * pantalla de ajustes se limita a leer el signal en vez de repetir el GET.
   */
  loadOnce(): void {
    if (this.currentClub() !== undefined) {
      return;
    }
    this.load().subscribe({ error: () => undefined });
  }

  /**
   * Cambia el nombre del club (solo ADMIN; el backend lo re-comprueba, ADR-0009). Guarda la
   * respuesta del servidor —no el valor optimista del formulario— para que la cabecera refleje el
   * estado autoritativo sin recargar.
   */
  rename(nombre: string): Observable<Club> {
    return from(this.api.actualizarClub({ body: { nombre } })).pipe(
      tap((club) => this.currentClub.set(club)),
    );
  }

  /** Vacía la caché (al cerrar sesión): otro usuario puede pertenecer a otro club. */
  reset(): void {
    this.currentClub.set(undefined);
  }
}
