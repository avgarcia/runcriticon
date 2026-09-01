package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura y escritura de las marcas del alumno (LAL-31). Puerto propio, no una vista de otra proyección: la
 * marca es su propio agregado, sin relación con `plan_resuelto_por_alumno`/`reporte_sesion`.
 */
interface StudentMarkRepository {
    /** Las marcas ya registradas de [studentId], solo las distancias con valor — el caso de uso completa las
     * cuatro distancias del catálogo con `null` para las que faltan. */
    fun findAll(
        clubId: ClubId,
        studentId: StudentId,
    ): Map<RaceDistance, StudentMark>

    /** Crea o reemplaza la marca de [studentId] en [StudentMark.distance] — envío idempotente, una marca por
     * distancia, sin histórico. */
    fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        mark: StudentMark,
    )

    /** Borra la marca de [studentId] en [distance]. Devuelve `true` solo si de verdad había una fila — el
     * caso de uso lo usa para decidir si emite `MarcaRetirada`. */
    fun delete(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
    ): Boolean
}
