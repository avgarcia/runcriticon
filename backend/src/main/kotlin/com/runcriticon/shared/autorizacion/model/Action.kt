package com.runcriticon.shared.autorizacion.model

/**
 * Acción que un [Role] puede ejercer sobre un [Resource] (ADR-0009 D6). Los valores concretos
 * se añaden por feature en Fase 1.
 */
enum class Action {
    /** Invitar (dar de alta) un recurso de identidad. */
    INVITE,
}
