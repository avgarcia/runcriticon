package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.person.AssignedGroup
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.api.rest.CoachGroupResponse
import com.runcriticon.shared.api.rest.CoachWorkloadResponse
import com.runcriticon.shared.api.rest.CoachWorkloadsResponse

/**
 * Traduce el listado de entrenadores con su carga a los modelos del contrato. `grupos` viaja como objetos completos
 * (a diferencia de `valores` en `StudentRestMapper`, que solo manda ids): aquí no hay una taxonomía cargada en el
 * cliente contra la que cruzar, así que el nombre del grupo viaja tal cual.
 */
internal fun List<CoachWorkload>.toResponse(): CoachWorkloadsResponse =
    CoachWorkloadsResponse(entrenadores = map { it.toResponse() })

internal fun CoachWorkload.toResponse(): CoachWorkloadResponse =
    CoachWorkloadResponse(
        id = id.value,
        nombre = name,
        email = email,
        estado = status.toCoachResponse(),
        grupos = groups.map { it.toResponse() },
        totalAlumnos = totalStudents,
    )

private fun AssignedGroup.toResponse(): CoachGroupResponse =
    CoachGroupResponse(id = id.value, nombre = name, totalAlumnos = totalStudents)

private fun PersonStatus.toCoachResponse(): CoachWorkloadResponse.Estado =
    when (this) {
        PersonStatus.INVITADO -> CoachWorkloadResponse.Estado.INVITADO
        PersonStatus.ACTIVO -> CoachWorkloadResponse.Estado.ACTIVO
    }
