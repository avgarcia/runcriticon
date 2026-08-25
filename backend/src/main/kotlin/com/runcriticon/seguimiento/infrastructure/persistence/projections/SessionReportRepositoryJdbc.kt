package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.SessionReportRepository
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDate

/**
 * Adaptador de [SessionReportRepository] sobre `JdbcTemplate`. Sin `@Entity`: este módulo va 100 % JDBC, mismo
 * criterio que el resto de `seguimiento`/`planificacion`.
 */
@Repository
class SessionReportRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : SessionReportRepository {
    @AuthScope(Scope.CLUB)
    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        planId: PlanId,
        day: LocalDate,
        report: SessionReport,
    ) {
        jdbc.update(
            UPSERT_SQL,
            studentId.value,
            planId.value,
            day,
            clubId.value,
            report.status.name,
            report.rating,
            report.reason?.name,
            report.notes,
            report.painFlag,
            Timestamp.from(report.reportedAt),
        )
    }
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.
private val UPSERT_SQL =
    """
    INSERT INTO seguimiento.reporte_sesion
        (alumno_id, plan_id, dia, club_id, estado, valoracion, motivo, notas, marca_dolor, reportado_en,
         actualizado_en)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
    ON CONFLICT (alumno_id, plan_id, dia) DO UPDATE SET
        club_id         = EXCLUDED.club_id,
        estado          = EXCLUDED.estado,
        valoracion      = EXCLUDED.valoracion,
        motivo          = EXCLUDED.motivo,
        notas           = EXCLUDED.notas,
        marca_dolor     = EXCLUDED.marca_dolor,
        reportado_en    = EXCLUDED.reportado_en,
        actualizado_en  = now()
    """.trimIndent()
