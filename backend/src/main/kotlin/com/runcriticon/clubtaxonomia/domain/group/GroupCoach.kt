package com.runcriticon.clubtaxonomia.domain.group

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus

/**
 * Entrenador asignado a un grupo, con lo mínimo para identificarlo y pintarlo.
 *
 * No reutiliza `Person` a propósito: aquella representa a cualquier persona del club y no conoce el grupo desde el
 * que se le mira, y no reutiliza [GroupMember] porque ese modela un alumno (sin email ni estado, que aquí sí hacen
 * falta) — mismo criterio de "no compartir tipo solo porque las formas se parecen" que ya fija [StudentSummary].
 */
data class GroupCoach(
    val id: PersonId,
    val name: String,
    val email: String,
    val status: PersonStatus,
)
