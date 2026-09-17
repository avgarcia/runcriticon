package com.runcriticon.seguimiento.domain

import java.time.Instant

/**
 * El reporte de sesión más reciente de los alumnos de un grupo, para la vista de salud del club.
 *
 * Sin sujeto identificable a propósito: agregado por grupo, nunca por alumno. [lastReportedAt] no es
 * nullable porque un grupo sin ningún reporte simplemente no produce fila — ver [ClubHealthReader].
 */
data class GroupActivity(
    val groupId: GroupId,
    val lastReportedAt: Instant,
)
