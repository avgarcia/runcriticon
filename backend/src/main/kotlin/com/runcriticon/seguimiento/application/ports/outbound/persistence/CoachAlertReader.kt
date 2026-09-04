package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.CoachAlert
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.shared.tenancy.ClubId
import java.time.LocalDate

/**
 * Lectura de las alertas activas del entrenador (LAL-116): computada a petición contra
 * `reporte_sesion`/`plan_resuelto_por_alumno`, **nunca** materializada — no hay tabla de alertas ni de
 * descartadas en el MVP (panel de solo lectura). Corre dentro de una petición con `Principal`, de ahí
 * `@AuthScope(Scope.CLUB)` en la implementación.
 *
 * Nunca lee de `seguimiento.marca_alumno`: la matriz de autorización prohíbe cualquier fila
 * ADMIN/ENTRENADOR sobre [com.runcriticon.shared.autorizacion.model.Resource.MARCA], ni siquiera para
 * lectura agregada (ver su KDoc) — este puerto no debe convertirse en la puerta trasera de esa barrera.
 */
interface CoachAlertReader {
    /**
     * Alertas activas de los alumnos de los grupos que lleva [coachId], acotadas a [groupId] si se indica
     * (un grupo ajeno al entrenador simplemente no aparece — el filtro va embebido en la consulta, no hay
     * `isCoachOfGroup` aparte porque no hay mutación que autorizar, solo una lectura ya acotada).
     *
     * La ventana de "activo" es de 7 días naturales hasta [today] para dolor y ritmo fuera de objetivo —
     * sin ella, un reporte con dolor de hace meses seguiría apareciendo para siempre, contradiciendo "panel
     * por excepción, no ruido". Para "sin reportar" el corte es de más de 7 días desde el último reporte
     * (o desde que el alumno tiene plan, si nunca reportó) — ver `CoachAlertReaderJdbc` para el detalle.
     */
    fun findActiveAlerts(
        clubId: ClubId,
        coachId: CoachId,
        groupId: GroupId?,
        today: LocalDate,
    ): List<CoachAlert>
}
