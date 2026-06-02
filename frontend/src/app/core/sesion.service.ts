import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

/** Representación de la sesión que devuelve el backend (GET/POST /api/sesion). */
export interface Sesion {
  userId: string;
  clubId: string;
  rol: string;
}

/**
 * Estado de sesión de la SPA (ADR-0003, ADR-0012). El CSRF lo gestiona HttpClient (cookie
 * XSRF-TOKEN -> header X-XSRF-TOKEN, configurado en app.config). La cookie de sesión httpOnly la
 * adjunta el navegador sola al ser mismo origen.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly http = inject(HttpClient);

  private readonly sesionActual = signal<Sesion | null>(null);

  /** Sesión en curso (solo lectura); `null` si no hay sesión iniciada. */
  readonly sesion = this.sesionActual.asReadonly();

  iniciar(email: string, password: string): Observable<Sesion> {
    return this.http
      .post<Sesion>('/api/sesion', { email, password })
      .pipe(tap((sesion) => this.sesionActual.set(sesion)));
  }

  cargarActual(): Observable<Sesion> {
    return this.http
      .get<Sesion>('/api/sesion/actual')
      .pipe(tap((sesion) => this.sesionActual.set(sesion)));
  }

  cerrar(): Observable<void> {
    return this.http
      .post<void>('/api/sesion/cierre', {})
      .pipe(tap(() => this.sesionActual.set(null)));
  }
}
