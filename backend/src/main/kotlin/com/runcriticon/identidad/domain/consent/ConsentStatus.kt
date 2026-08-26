package com.runcriticon.identidad.domain.consent

/**
 * Estado de salida del consentimiento del propio alumno (`GET /me/consentimiento`), derivado de la
 * última fila de [Consent] — no es un valor persistido, solo la vista que consume el contrato REST.
 */
enum class ConsentStatus {
    /** Nunca ha concedido consentimiento (cuenta activada antes de que existiera este mecanismo). */
    PENDIENTE,

    /** Tiene una fila vigente (sin revocar) sobre la versión de texto actual. */
    VIGENTE,

    /** Su última fila está revocada. */
    REVOCADO,
}
