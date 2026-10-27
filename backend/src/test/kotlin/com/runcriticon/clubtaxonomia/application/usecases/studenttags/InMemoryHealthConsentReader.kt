package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.HealthConsentReader
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria de [HealthConsentReader]. Concede por defecto, para no tocar los tests de clasificación que no
 * versan sobre el tag de lesión; [revoked] retira el consentimiento a alumnos concretos.
 */
class InMemoryHealthConsentReader(
    private val revoked: Set<PersonId> = emptySet(),
) : HealthConsentReader {
    override fun isGranted(
        clubId: ClubId,
        studentId: PersonId,
    ): Boolean = studentId !in revoked

    override fun grantedAmong(
        clubId: ClubId,
        students: Set<PersonId>,
    ): Set<PersonId> = students - revoked
}
