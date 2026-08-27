package com.runcriticon.planificacion.domain

/**
 * Errores del módulo planificación.
 *
 * Se devuelven como `Either<PlanificacionError, T>` (Raise DSL); el dominio nunca lanza excepción de negocio.
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

    /** El plan ya está `PUBLICADO`: ni se republica ni se le tocan sesiones (LAL-25, congelación). */
    data object PlanAlreadyPublished : PlanificacionError()

    /** El plan no tiene ninguna sesión — publicar una semana en blanco es siempre un error del entrenador. */
    data object NoSessions : PlanificacionError()

    /**
     * La proyección `miembro_grupo` está atrasada más de lo tolerable (ADR-0009 D9, fail-closed a 60 s):
     * publicar ahora arriesgaría un snapshot incompleto. [lagSeconds] es el retraso medido en el momento del
     * rechazo, para el log de auditoría — no se expone al cliente.
     */
    data class ProjectionStale(
        val lagSeconds: Long,
    ) : PlanificacionError()

    /** No hay ninguna personalización de ese alumno en esa sesión (LAL-26: `RemovePersonalizationCommand`). */
    data object PersonalizationNotFound : PlanificacionError()

    /**
     * El alumno no pertenece al grupo (plan en `BORRADOR`) o no está en el snapshot congelado (plan ya
     * `PUBLICADO`) — LAL-26, AC2/AC3. A diferencia de [Forbidden], no es un problema de permisos del
     * entrenador: el entrenador sí puede personalizar, pero no a un alumno que no está en el plan.
     */
    data object StudentNotInPlan : PlanificacionError()
}
