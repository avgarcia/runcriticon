package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Escritura de la proyección `consentimiento_alumno`, alimentada por `ConsentimientoConcedido`/
 * `ConsentimientoRevocado` (`ConsentProjectionListener`, LAL-128 PR2). Puerto aparte de [ConsentReader], mismo
 * criterio que `ResolvedPlanProjection`/`ResolvedPlanReader`: este corre en el listener del outbox sin
 * principal, aquel dentro de una petición con `Principal` y se somete al filtro de club.
 */
interface ConsentProjection {
    /**
     * Upsert por `alumno_id` con guarda de orden por `occurredAt`: los dos eventos pueden reentregarse o
     * llegar desordenados desde el outbox, y el más reciente siempre gana — mismo criterio que
     * `GroupMembersProjectionJdbc.UPSERT_SQL`.
     */
    fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        granted: Boolean,
        textVersion: String,
        eventId: UUID,
        occurredAt: Instant,
    )

    fun deleteByStudentId(studentId: StudentId): Int
}
