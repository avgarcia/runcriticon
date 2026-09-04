package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.application.usecases.alerts.ListCoachAlertsQuery
import com.runcriticon.seguimiento.domain.CoachAlert
import com.runcriticon.shared.api.rest.AlertasResponse
import com.runcriticon.shared.api.rest.DolorReportadoAlert
import com.runcriticon.shared.api.rest.RitmoFueraDeObjetivoAlert
import com.runcriticon.shared.api.rest.SinReportarAlert
import java.time.ZoneOffset
import com.runcriticon.shared.api.rest.CoachAlert as CoachAlertResponse

/** Las alertas activas, para `GET /alertas`. */
internal fun ListCoachAlertsQuery.Result.toResponse(): AlertasResponse =
    AlertasResponse(alertas = alerts.map { it.toResponse() })

private fun CoachAlert.toResponse(): CoachAlertResponse =
    when (this) {
        is CoachAlert.PainReported ->
            DolorReportadoAlert(
                tipo = DolorReportadoAlert.Tipo.DOLOR_REPORTADO,
                alumnoId = studentId.value,
                grupoId = groupId.value,
                dia = day,
                reportadoEn = reportedAt.atOffset(ZoneOffset.UTC),
                notas = notes,
            )

        is CoachAlert.NoReportInDays ->
            SinReportarAlert(
                tipo = SinReportarAlert.Tipo.SIN_REPORTAR,
                alumnoId = studentId.value,
                grupoId = groupId.value,
                diasSinReportar = daysSinceLastReport.toInt(),
                ultimoReporteEn = lastReportedAt?.atOffset(ZoneOffset.UTC),
            )

        is CoachAlert.PaceOffTarget ->
            RitmoFueraDeObjetivoAlert(
                tipo = RitmoFueraDeObjetivoAlert.Tipo.RITMO_FUERA_DE_OBJETIVO,
                alumnoId = studentId.value,
                grupoId = groupId.value,
                dia = day,
                notas = notes,
            )
    }
