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
}
