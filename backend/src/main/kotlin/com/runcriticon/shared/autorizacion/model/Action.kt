package com.runcriticon.shared.autorizacion.model

/**
 * Acción que un [Role] puede ejercer sobre un [Resource] (ADR-0009 D6). Los valores concretos
 * (READ, CREATE, UPDATE, DELETE, etc.) se añaden por feature en Fase 1; en H0 está vacío.
 */
enum class Action
