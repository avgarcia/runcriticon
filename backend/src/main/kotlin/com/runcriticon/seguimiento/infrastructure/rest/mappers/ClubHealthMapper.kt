package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.domain.GroupActivity
import com.runcriticon.shared.api.rest.ActividadDeGrupoResponse
import com.runcriticon.shared.api.rest.ActividadPorGrupoResponse
import java.time.ZoneOffset

/** La última actividad de cada grupo, para `GET /salud-del-club/actividad`. */
internal fun List<GroupActivity>.toResponse(): ActividadPorGrupoResponse =
    ActividadPorGrupoResponse(grupos = map { it.toResponse() })

private fun GroupActivity.toResponse(): ActividadDeGrupoResponse =
    ActividadDeGrupoResponse(
        grupoId = groupId.value,
        ultimaActividadEn = lastReportedAt.atOffset(ZoneOffset.UTC),
    )
