import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

/** Representación de la sesión que devuelve el backend (GET/POST /api/sesion). */
export interface Session {
  userId: string;
  clubId: string;
  role: string;
}

/**
 * Estado de sesión de la SPA (ADR-0003, ADR-0012). El CSRF lo gestiona HttpClient (cookie
 * XSRF-TOKEN -> header X-XSRF-TOKEN, configurado en app.config). La cookie de sesión httpOnly la
 * adjunta el navegador sola al ser mismo origen.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  private readonly currentSession = signal<Session | null>(null);

  /** Sesión en curso (solo lectura); `null` si no hay sesión iniciada. */
  readonly session = this.currentSession.asReadonly();

  start(email: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/sesion', { email, password })
      .pipe(tap((session) => this.currentSession.set(session)));
  }

  loadCurrent(): Observable<Session> {
    return this.http
      .get<Session>('/api/sesion/actual')
      .pipe(tap((session) => this.currentSession.set(session)));
  }

  close(): Observable<void> {
    return this.http
      .post<void>('/api/sesion/cierre', {})
      .pipe(tap(() => this.currentSession.set(null)));
  }
}
