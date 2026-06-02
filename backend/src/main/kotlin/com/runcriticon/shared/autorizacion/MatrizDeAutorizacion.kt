package com.runcriticon.shared.autorizacion

/**
 * Matriz de autorización RBAC (ADR-0009 D6, ADR-0003 D2). Es la única fuente de verdad de
 * "qué [Rol] puede ejecutar qué [Accion] sobre qué [Recurso]". Se consulta desde el guardado
 * de cada caso de uso, nunca desde el dominio.
 *
 * En H0 el conjunto de reglas está vacío: las reglas se declaran por feature en Fase 1. El
 * default es deny (si no hay regla explícita, [puede] devuelve `false`).
 */
object MatrizDeAutorizacion {
    private val reglas: Set<Triple<Rol, Recurso, Accion>> = emptySet()

    fun puede(rol: Rol, recurso: Recurso, accion: Accion): Boolean =
        Triple(rol, recurso, accion) in reglas
}
