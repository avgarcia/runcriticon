package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.GroupActivity
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura agregada para la vista de salud del club: la última actividad reportada de cada grupo. Corre
 * dentro de una petición con `Principal`, de ahí `@AuthScope(Scope.CLUB)` en la implementación.
 *
 * Nunca lee de `seguimiento.marca_alumno`: la matriz de autorización prohíbe cualquier fila
 * ADMIN/ENTRENADOR sobre [com.runcriticon.shared.autorizacion.model.Resource.MARCA], ni siquiera para
 * lectura agregada (ver su KDoc) — este puerto no debe convertirse en la puerta trasera de esa barrera.
 */
interface ClubHealthReader {
    /**
     * Un [GroupActivity] por cada grupo de [clubId] que tenga al menos un reporte de sesión — un grupo sin
     * ninguno simplemente no aparece, no produce una fila con `null`.
     */
    fun findLastActivityByGroup(clubId: ClubId): List<GroupActivity>
}
