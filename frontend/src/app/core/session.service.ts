import { Injectable, inject, signal } from '@angular/core';
import { Observable, from, tap } from 'rxjs';
import { SesionService } from '../api/generated/services/sesion.service';
import { SessionResponse } from '../api/generated/models/session-response';

/** Representación de la sesión que devuelve el backend (alias del modelo generado del contrato). */
export type Session = SessionResponse;

/**
 * Estado de sesión de la SPA (ADR-0003, ADR-0012). Delega el HTTP en el cliente generado desde el
 * contrato OpenAPI (`SesionService`) — nada de URLs a mano (frontend/CLAUDE.md). El CSRF lo gestiona
 * HttpClient (cookie XSRF-TOKEN -> header X-XSRF-TOKEN, app.config); la cookie httpOnly la adjunta el
 * navegador sola al ser mismo origen.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(SesionService);

  private readonly currentSession = signal<Session | null>(null);

  /** Sesión en curso (solo lectura); `null` si no hay sesión iniciada. */
  readonly session = this.currentSession.asReadonly();

  /**
   * Credenciales caducadas que se pasan del login a la pantalla de cambio obligatorio (ADR-0003 D7).
   * Viven SOLO en memoria (nunca en localStorage/sessionStorage, ADR-0003) y se consumen una vez.
   */
  private expiredCredentials: { email: string; password: string } | null = null;

  start(email: string, password: string): Observable<Session> {
    return from(this.api.iniciarSesion({ body: { email, password } })).pipe(
      tap((session) => this.currentSession.set(session)),
    );
  }

  loadCurrent(): Observable<Session> {
    return from(this.api.consultarSesion()).pipe(
      tap((session) => this.currentSession.set(session)),
    );
  }

  close(): Observable<void> {
    return from(this.api.cerrarSesion()).pipe(tap(() => this.currentSession.set(null)));
  }

  /** Cambio forzado de contraseña caducada (ADR-0003 D7): al lograrlo, el backend inicia la sesión. */
  changeExpiredPassword(
    email: string,
    currentPassword: string,
    newPassword: string,
  ): Observable<Session> {
    return from(
      this.api.cambiarContrasenaCaducada({ body: { email, currentPassword, newPassword } }),
    ).pipe(tap((session) => this.currentSession.set(session)));
  }

  /** Solicita un magic link de login (ADR-0003 D5). Respuesta neutra: no revela si el email existe. */
  requestMagicLink(email: string): Observable<void> {
    return from(this.api.solicitarMagicLink({ body: { email } }));
  }

  /** Consume un magic link (token del email) e inicia sesión (ADR-0003 D5). */
  consumeMagicLink(token: string): Observable<Session> {
    return from(this.api.consumirMagicLink({ body: { token } })).pipe(
      tap((session) => this.currentSession.set(session)),
    );
  }

  /**
   * Solicita un reseteo de contraseña (ADR-0003 D8). Respuesta neutra: no revela si el email existe.
   */
  requestPasswordReset(email: string): Observable<void> {
    return from(this.api.solicitarReseteo({ body: { email } }));
  }

  /**
   * Consume un reseteo (token del email + contraseña nueva) e inicia sesión (ADR-0003 D8). El backend
   * invalida el resto de sesiones activas del usuario al fijar la contraseña.
   */
  consumePasswordReset(token: string, newPassword: string): Observable<Session> {
    return from(this.api.consumirReseteo({ body: { token, newPassword } })).pipe(
      tap((session) => this.currentSession.set(session)),
    );
  }

  /** Guarda en memoria las credenciales caducadas para la pantalla de cambio (handoff login -> cambio). */
  stashExpiredCredentials(email: string, password: string): void {
    this.expiredCredentials = { email, password };
  }

  /** Devuelve y limpia las credenciales caducadas (un solo uso). */
  takeExpiredCredentials(): { email: string; password: string } | null {
    const credentials = this.expiredCredentials;
    this.expiredCredentials = null;
    return credentials;
  }
}
