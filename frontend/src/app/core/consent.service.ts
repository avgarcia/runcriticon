import { Injectable, inject } from '@angular/core';
import { Observable, from } from 'rxjs';
import { IdentidadService } from '../api/generated/services/identidad.service';
import { MiConsentimientoResponse } from '../api/generated/models/mi-consentimiento-response';

/** El propio estado de consentimiento de datos de salud (alias del modelo generado, LAL-128). */
export type MyConsent = MiConsentimientoResponse;

/**
 * Versión vigente del texto de consentimiento (ADR-0014 D18). Debe coincidir con
 * `ConsentText.CURRENT_VERSION` del backend — si difieren, el backend rechaza con
 * `VERSION_CONSENTIMIENTO_OBSOLETA` y hay que desplegar frontend y backend juntos, no es un fallo
 * silencioso: el alumno ve el error y no consiente sobre un texto que ya no es el vigente.
 */
export const CONSENT_TEXT_VERSION = 'v2026-08-25';

/**
 * Consentimiento explícito de datos de salud del propio alumno (Art. 9.2.a RGPD, LAL-128). Sin signal
 * de estado propio: se usa en un único punto (`/mi-cuenta`) que siempre recarga al entrar, mismo
 * criterio que `MyPlanService`.
 */
@Injectable({ providedIn: 'root' })
export class ConsentService {
  private readonly api = inject(IdentidadService);

  /** El propio estado (`PENDIENTE`/`VIGENTE`/`REVOCADO`). */
  getMyConsent(): Observable<MyConsent> {
    return from(this.api.consultarMiConsentimiento());
  }

  /** Conceder, o volver a conceder tras revocar. Idempotente si ya está vigente. */
  grant(): Observable<MyConsent> {
    return from(
      this.api.concederMiConsentimiento({ body: { versionConsentimiento: CONSENT_TEXT_VERSION } }),
    );
  }

  /** Revocar. Tras esto, seguimiento deja de aceptar nuevos reportes de sesión. */
  revoke(): Observable<MyConsent> {
    return from(this.api.revocarMiConsentimiento());
  }
}
