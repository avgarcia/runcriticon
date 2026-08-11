package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.shared.api.rest.StudentSummaryResponse
import com.runcriticon.shared.api.rest.StudentsResponse

/**
 * Traduce el listado de alumnos a los modelos del contrato. `valores` viaja como lista de ids, igual que
 * `GroupSummaryResponse`: el cliente compone el rótulo cruzando con la taxonomía que ya tiene cargada.
 */
internal fun List<StudentSummary>.toResponse(): StudentsResponse = StudentsResponse(alumnos = map { it.toResponse() })

internal fun StudentSummary.toResponse(): StudentSummaryResponse =
    StudentSummaryResponse(
        id = id.value,
        nombre = name,
        email = email,
        estado = status.toResponse(),
        valores = tagValueIds.map { it.value },
    )

private fun PersonStatus.toResponse(): StudentSummaryResponse.Estado =
    when (this) {
        PersonStatus.INVITADO -> StudentSummaryResponse.Estado.INVITADO
        PersonStatus.ACTIVO -> StudentSummaryResponse.Estado.ACTIVO
    }
