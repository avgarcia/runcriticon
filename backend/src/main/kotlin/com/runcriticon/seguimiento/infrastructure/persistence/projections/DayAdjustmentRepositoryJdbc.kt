package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.DayAdjustmentRepository
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

/**
 * Adaptador de [DayAdjustmentRepository] sobre `JdbcTemplate` (LAL-33). Sin `@Entity`: este módulo va 100 %
 * JDBC, mismo criterio que el resto de `seguimiento`.
 *
 * Sin `Scope.OWNED`: el aspecto de autorización no lo implementa todavía y falla cerrado (lección de
 * LAL-29/LAL-30) — `studentId` nunca llega de un parámetro de entrada, siempre de `actor.userId` en el caso
 * de uso.
 */
@Repository
class DayAdjustmentRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : DayAdjustmentRepository {
    @AuthScope(Scope.CLUB)
    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        planId: PlanId,
        adjustment: DayAdjustment,
    ) {
        jdbc.update(
            UPSERT_SQL,
            studentId.value,
            planId.value,
            adjustment.plannedDay,
            clubId.value,
            adjustment.operationId,
            adjustment.action.name,
            adjustment.targetDay,
            adjustment.reason.name,
            adjustment.message,
            adjustment.painFlag,
            Timestamp.from(adjustment.createdAt),
        )
    }

    @AuthScope(Scope.CLUB)
    override fun deleteByOperation(
        clubId: ClubId,
        studentId: StudentId,
        operationId: UUID,
    ): Int = jdbc.update(DELETE_BY_OPERATION_SQL, studentId.value, operationId, clubId.value)
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.
private val UPSERT_SQL =
    """
    INSERT INTO seguimiento.reajuste_dia
        (alumno_id, plan_id, dia, club_id, operacion_id, accion, dia_destino, motivo, mensaje, marca_dolor,
         creado_en, actualizado_en)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
    ON CONFLICT (alumno_id, plan_id, dia) DO UPDATE SET
        club_id         = EXCLUDED.club_id,
        operacion_id    = EXCLUDED.operacion_id,
        accion          = EXCLUDED.accion,
        dia_destino     = EXCLUDED.dia_destino,
        motivo          = EXCLUDED.motivo,
        mensaje         = EXCLUDED.mensaje,
        marca_dolor     = EXCLUDED.marca_dolor,
        actualizado_en  = now()
    """.trimIndent()

private const val DELETE_BY_OPERATION_SQL =
    "DELETE FROM seguimiento.reajuste_dia WHERE alumno_id = ? AND operacion_id = ? AND club_id = ?"
