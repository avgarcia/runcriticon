package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.time.LocalDate

/**
 * Lectura de la proyección `plan_resuelto_por_alumno` para la vista del propio alumno. A diferencia de
 * [ResolvedPlanProjection], corre dentro de una petición con `Principal` — de ahí `@AuthScope(Scope.CLUB)` en
 * la implementación, nunca `@NoAuthScope`. Sin `Scope.OWNED`: ese scope no tiene verificación implementada por
 * `AuthScopeEnforcementAspect` todavía, y [studentId] nunca llega de entrada de usuario (siempre
 * `StudentId.of(actor.userId)`), así que no hay IDOR que cerrar con él.
 */
interface ResolvedPlanReader {
    /**
     * Las sesiones resueltas de [studentId] entre [from] y [to] (inclusive), una por día. Si [studentId]
     * pertenece a dos grupos cuyos planes resuelven el mismo día, la implementación desempata de forma
     * determinista y solo devuelve una fila por día — el desempate real (avisar al alumno, priorizar por
     * criterio de negocio) queda diferido; ver el README del módulo.
     */
    fun findWeek(
        clubId: ClubId,
        studentId: StudentId,
        from: LocalDate,
        to: LocalDate,
    ): List<ResolvedSession>
}
