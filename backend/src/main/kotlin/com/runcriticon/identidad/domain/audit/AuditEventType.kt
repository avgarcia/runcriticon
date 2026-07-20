package com.runcriticon.identidad.domain.audit

/**
 * Tipo de evento auditable de identidad. Los valores en castellano son los que se persisten en la columna `tipo` de
 * `identidad.evento_auditoria`.
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
    MAGIC_LINK_RATE_LIMITED,
    RESETEO_RATE_LIMITED,
    INVITACION_RATE_LIMITED,
}
