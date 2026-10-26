package com.runcriticon.seguimiento.application.usecases.report

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentReader
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId

/** Doble en memoria del puerto de lectura de consentimiento. `granted = true` por defecto para no romper los
 * tests existentes de [com.runcriticon.seguimiento.application.usecases.report.SubmitSessionReportCommand]
 * que no versan sobre esta guarda. [revoked] revoca alumnos concretos sin tocar el resto, para los listados
 * que mezclan alumnos con y sin consentimiento. */
class InMemoryConsentReader(
    private val granted: Boolean = true,
    private val revoked: Set<StudentId> = emptySet(),
) : ConsentReader {
    val calls: MutableList<Pair<ClubId, StudentId>> = mutableListOf()

    override fun isGranted(
        clubId: ClubId,
        studentId: StudentId,
    ): Boolean {
        calls += clubId to studentId
        return granted && studentId !in revoked
    }

    override fun grantedAmong(
        clubId: ClubId,
        students: Set<StudentId>,
    ): Set<StudentId> = students.filterTo(mutableSetOf()) { isGranted(clubId, it) }
}
