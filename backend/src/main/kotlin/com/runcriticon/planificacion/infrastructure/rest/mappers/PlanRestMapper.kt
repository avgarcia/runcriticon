package com.runcriticon.planificacion.infrastructure.rest.mappers

import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.api.rest.PlanResponse
import com.runcriticon.shared.api.rest.PlanesResponse

/** Traduce `WeeklyPlan` a su modelo del contrato. Sin sesiones ni personalizaciones: ver `PlanResponse` en el spec. */
internal fun WeeklyPlan.toResponse(): PlanResponse =
    PlanResponse(
        id = id.value,
        grupoId = groupId.value,
        semana = week,
        estado = status.toPlanResponse(),
    )

internal fun List<WeeklyPlan>.toResponse(): PlanesResponse = PlanesResponse(planes = map { it.toResponse() })

private fun PlanStatus.toPlanResponse(): PlanResponse.Estado =
    when (this) {
        PlanStatus.BORRADOR -> PlanResponse.Estado.BORRADOR
        PlanStatus.PUBLICADO -> PlanResponse.Estado.PUBLICADO
    }
