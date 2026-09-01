package com.runcriticon.seguimiento.application.usecases.marks

import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkRepository
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.tenancy.ClubId

/** Registro de una llamada a [StudentMarkRepository.upsert]. */
data class StudentMarkUpsertCall(
    val clubId: ClubId,
    val studentId: StudentId,
    val mark: StudentMark,
)

/** Doble en memoria del puerto de marcas, registrando con qué se le llamó — mismo patrón que
 * `InMemorySessionReportRepository`. */
class InMemoryStudentMarkRepository(
    initial: Map<RaceDistance, StudentMark> = emptyMap(),
) : StudentMarkRepository {
    private val marks: MutableMap<RaceDistance, StudentMark> = initial.toMutableMap()
    val upsertCalls: MutableList<StudentMarkUpsertCall> = mutableListOf()
    val deleteCalls: MutableList<RaceDistance> = mutableListOf()

    override fun findAll(
        clubId: ClubId,
        studentId: StudentId,
    ): Map<RaceDistance, StudentMark> = marks.toMap()

    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        mark: StudentMark,
    ) {
        upsertCalls += StudentMarkUpsertCall(clubId, studentId, mark)
        marks[mark.distance] = mark
    }

    override fun delete(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
    ): Boolean {
        deleteCalls += distance
        return marks.remove(distance) != null
    }
}
