package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.studenttags.AssignedTagValue
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
import com.runcriticon.shared.api.rest.StudentTagResponse
import com.runcriticon.shared.api.rest.StudentTagsResponse
import java.time.ZoneOffset

/**
 * Traduce la clasificación de un alumno a los modelos del contrato.
 *
 * Cada valor viaja con el eje del que cuelga: el cliente agrupa los chips por eje y no puede deducirlo del id del
 * valor. El orden lo fija el dominio, no este mapeador.
 */
internal fun StudentTags.toResponse(): StudentTagsResponse =
    StudentTagsResponse(
        alumnoId = studentId.value,
        valores = assigned.map { it.toResponse() },
    )

internal fun AssignedTagValue.toResponse(): StudentTagResponse =
    StudentTagResponse(
        tagId = keyId.value,
        id = value.id.value,
        valor = value.label.value,
        archivadoEn = value.archivedAt?.atOffset(ZoneOffset.UTC),
    )
