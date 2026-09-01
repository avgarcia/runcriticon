import { Injectable, inject } from '@angular/core';
import { Observable, from } from 'rxjs';
// El servicio generado del contrato se llama `SeguimientoService` (mismo tag que `/me/plan` y
// `/me/reportes`): se importa aliasado, mismo criterio que `my-plan.service.ts`.
import { SeguimientoService as MyMarksApi } from '../api/generated/services/seguimiento.service';
import { MiMarcaResponse } from '../api/generated/models/mi-marca-response';
import { MisMarcasResponse } from '../api/generated/models/mis-marcas-response';

/** Las cuatro marcas del propio alumno, en orden fijo 5K/10K/21K/42K (LAL-31, alias del modelo generado). */
export type MyMarks = MisMarcasResponse;

/** Una marca (o su ausencia) para una distancia (alias del modelo generado). */
export type MyMark = MiMarcaResponse;

/** Distancia estándar de una marca — mismo literal que el contrato, sin enum propio. */
export type MarkDistance = MyMark['distancia'];

/**
 * Las propias marcas del alumno (LAL-31). Sin signal de estado propio, mismo criterio que
 * `MyPlanService`: la pantalla que lo usa vuelve a pedir la lista tras cada cambio, y cachear aquí
 * no aporta nada que una recarga no dé ya.
 */
@Injectable({ providedIn: 'root' })
export class MyMarksService {
  private readonly api = inject(MyMarksApi);

  getMarks(): Observable<MyMarks> {
    return from(this.api.consultarMisMarcas());
  }

  /** Envío idempotente (LAL-31): la primera vez crea la marca de esa distancia, las siguientes la
   * sobreescribe, sin histórico. */
  recordMark(distancia: MarkDistance, tiempoSegundos: number): Observable<MyMark> {
    return from(this.api.registrarMiMarca({ distancia, body: { tiempoSegundos } }));
  }

  /** Idempotente: no falla si la distancia no tenía marca todavía. */
  withdrawMark(distancia: MarkDistance): Observable<void> {
    return from(this.api.retirarMiMarca({ distancia }));
  }
}
