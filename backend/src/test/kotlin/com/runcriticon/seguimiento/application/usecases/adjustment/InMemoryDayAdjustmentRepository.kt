package com.runcriticon.seguimiento.application.usecases.adjustment

import com.runcriticon.seguimiento.application.ports.outbound.persistence.DayAdjustmentRepository
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/** Registro de una llamada a [DayAdjustmentRepository.upsert]. */
data class DayAdjustmentUpsertCall(
    val clubId: ClubId,
    val studentId: StudentId,
    val planId: PlanId,
    val adjustment: DayAdjustment,
)

/** Doble en memoria del puerto de escritura, registrando con qué se le llamó. */
class InMemoryDayAdjustmentRepository : DayAdjustmentRepository {
    val calls: MutableList<DayAdjustmentUpsertCall> = mutableListOf()
    val deletedOperations: MutableList<UUID> = mutableListOf()

    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        planId: PlanId,
        adjustment: DayAdjustment,
    ) {
        calls += DayAdjustmentUpsertCall(clubId, studentId, planId, adjustment)
    }

    override fun deleteByOperation(
        clubId: ClubId,
        studentId: StudentId,
        operationId: UUID,
    ): Int {
        deletedOperations += operationId
        return 1
    }
}
