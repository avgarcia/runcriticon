package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Escritura de la proyección local `consentimiento_alumno` desde `HealthConsentListener`, sin principal. Puerto
 * aparte de [HealthConsentReader], que lee dentro de una petición: mismo reparto que en `seguimiento`.
 */
interface HealthConsentProjection {
    /**
     * Upsert por alumno con guarda de orden por `occurredAt`: conceder y revocar alternan sobre la misma fila y el
     * outbox no garantiza el orden de entrega, así que el evento más reciente siempre gana. No escribe nada si el
     * alumno tiene lápida de supresión: un evento rezagado no debe resucitar a alguien ya borrado.
     *
     * @return `true` si la fila quedó con este evento; `false` si ya reflejaba uno más reciente o el alumno está
     * suprimido — en ambos casos quien llama no debe actuar como si este evento fuera el vigente.
     */
    fun upsert(
        clubId: ClubId,
        studentId: PersonId,
        granted: Boolean,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean
}
