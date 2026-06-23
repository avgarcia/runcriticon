import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Acceso HTTP al recurso entrenadores (ADR-0001 D10).
 * Servicio manual hasta que se cablee el generador OpenAPI (build debt de LAL-48).
 */
@Injectable({ providedIn: 'root' })
export class CoachesService {
  private readonly http = inject(HttpClient);

  invite(name: string, email: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>('/api/entrenadores', { nombre: name, email });
  }
}
