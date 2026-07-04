package com.runcriticon.identidad.domain.audit

/**
 * Tipo de evento auditable de identidad (ADR-0003 D15). Los valores en castellano son los que se
 * persisten en la columna `tipo` de `identidad.evento_auditoria`. El enum crece por feature: hoy
 * solo se materializa el primero; el resto de tipos de D15 se añaden con su caso de uso.
 */
enum class AuditEventType {
    INVITACION_EMITIDA,
    INVITACION_ACTIVADA,
    PASSWORD_CAMBIADA,
    MAGIC_LINK_EMITIDO,
    MAGIC_LINK_USADO,
    RESETEO_INICIADO,
    SESION_REVOCADA,
    CUENTA_DESACTIVADA,

    // Rate-limiting de autenticación (ADR-0003 D12, LAL-35). Se registran con `email_hash` + `ip` en
    // `metadata` para que el admin pueda investigar un abuso sin que el email quede en claro.
    MAGIC_LINK_RATE_LIMITED,
    RESETEO_RATE_LIMITED,
    INVITACION_RATE_LIMITED,
}
