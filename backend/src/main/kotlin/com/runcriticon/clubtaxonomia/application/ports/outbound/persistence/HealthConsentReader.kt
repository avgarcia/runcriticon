package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura de la proyección `consentimiento_alumno` con principal en curso: la guarda de asignación del tag de
 * lesión.
 */
interface HealthConsentReader {
    /**
     * `false` tanto si el alumno revocó como si no hay fila — **fail-closed**, mismo criterio que el `ConsentReader`
     * de `seguimiento`: sin evidencia de consentimiento vigente no se trata el dato de salud.
     */
    fun isGranted(
        clubId: ClubId,
        studentId: PersonId,
    ): Boolean

    /**
     * Los de [students] con consentimiento vigente, mismo criterio fail-closed. En lote, para la clasificación en
     * masa: una consulta para todos los seleccionados en vez de una por alumno.
     */
    fun grantedAmong(
        clubId: ClubId,
        students: Set<PersonId>,
    ): Set<PersonId>
}
