package com.runcriticon.planificacion.domain

/**
 * Errores del módulo planificación.
 *
 * Se devuelven como `Either<PlanificacionError, T>` (Raise DSL); el dominio nunca lanza excepción de negocio.
 *
 * Variantes previstas que aún no se declaran (se añaden con su historia, para no dejar ramas `when` inalcanzables):
 *  - `PlanAlreadyPublished`, `NoSessions` → cuando exista `publish()` (LAL-25).
 *  - `ProjectionStale` → cuando `CoachGroupLookup` incorpore la puerta fail-closed de ADR-0009 D9 (LAL-25 la
 *    necesita para publicar; crear un borrador no, ver README del módulo).
 */
sealed class PlanificacionError {
    /**
     * El rol del llamador no puede ejecutar la operación, o es entrenador pero sin relación con el grupo (o el
     * plan no existe / no es de su club — mismo motivo que abajo: no distinguir "no existe" de "no es tuyo").
     *
     * Colapsa ambos casos a propósito, mismo motivo que `ClubTaxonomiaError.StudentNotFound`: un entrenador ajeno
     * a un grupo no debe poder distinguir "el grupo no existe" de "el grupo no es tuyo" comparando respuestas — y
     * este módulo, sin más que su proyección local, tampoco puede confirmar por sí solo que el grupo exista.
     */
    data object Forbidden : PlanificacionError()

    /** Entrada inválida del cliente. [field] y [reason] son estables para que la capa REST los traduzca. */
    data class InvalidInput(
        val field: String,
        val reason: String,
    ) : PlanificacionError()

    /** La sesión referenciada no existe en el plan (LAL-24: `updateSession`/`removeSession`). */
    data object SessionNotFound : PlanificacionError()

    /** Ya existe una sesión ese día del plan (LAL-24: `UNIQUE (plan_id, dia)`, una sesión por día). */
    data object DuplicateSessionDay : PlanificacionError()
}
