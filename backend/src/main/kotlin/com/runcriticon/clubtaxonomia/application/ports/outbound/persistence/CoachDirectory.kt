package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura de los entrenadores del club con su carga (grupos asignados, alumnos que suman) para la pantalla de
 * listado.
 *
 * Puerto aparte de [PersonProjection] a propósito, mismo motivo que ya separa [StudentDirectory] de ella: aquella es
 * el puerto de **escritura** de la proyección, sus métodos corren en listeners sin sesión; este corre dentro de una
 * petición con principal y se somete al filtro de club.
 */
interface CoachDirectory {
    /** Entrenadores del club, ordenados por nombre. Hoy `groups`/`totalStudents` salen vacíos (LAL-93). */
    fun listByClub(clubId: ClubId): List<CoachWorkload>
}
