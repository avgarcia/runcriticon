package com.runcriticon.identidad.domain.consent

/**
 * Versión vigente del texto de consentimiento de datos de salud (ADR-0014 D18). El texto en sí vive
 * versionado fuera del código, en `docs/legal/consentimiento/{version}.md` — este objeto solo fija qué
 * versión es la vigente hoy, para que [GrantConsentCommand][com.runcriticon.identidad.application.usecases.consent.GrantConsentCommand]
 * pueda rechazar una concesión sobre una versión obsoleta sin tener que leer el fichero en tiempo de
 * ejecución.
 *
 * **`CURRENT_VERSION` es un borrador pendiente de validación legal** (ver el fichero de texto): el
 * mecanismo de captura está completo, pero la redacción todavía no la ha revisado asesoría legal —
 * pendiente jurídico explícito de ADR-0014, no de este módulo.
 *
 * Cambiar de versión aquí, sin más, dispararía en el próximo login la re-confirmación de todos los
 * alumnos activos si D18 llega a implementar ese flujo — hoy no lo hace (fuera de alcance, ver README).
 */
object ConsentText {
    const val CURRENT_VERSION: String = "v2026-08-25"
}
