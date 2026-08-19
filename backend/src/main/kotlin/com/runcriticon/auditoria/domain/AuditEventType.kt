package com.runcriticon.auditoria.domain

/**
 * Tipo de asiento persistido en `auditoria.evento` — corresponde 1:1 con los dos integration events que consume
 * este módulo (ADR-0009 D15).
 */
enum class AuditEventType {
    ACCESO_DENEGADO,
    ACCESO_DATOS_SENSIBLES,
}
