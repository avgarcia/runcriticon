package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Comprueba contra la proyección local que una persona del club existe y es entrenador, antes de asignarla a un
 * grupo. Simétrico de [StudentLookup] y por el mismo motivo de puerto aparte de [PersonProjection].
 */
interface CoachLookup {
    /**
     * `true` solo si en [clubId] hay una persona con ese id **y rol entrenador**. Devuelve `Boolean` y no la
     * persona por el mismo motivo que [StudentLookup.isStudent]: los tres modos de fallo —no existe, es alumno, es
     * de otro club— dan la misma respuesta al cliente.
     *
     * **Toma un bloqueo sobre la persona que dura hasta el fin de la transacción**, mismo motivo que
     * [StudentLookup.isStudent]: sin él, entre esta comprobación y la escritura de la asignación cabría una
     * supresión, y el borrado dejaría la tabla limpia justo para que la asignación volviera a escribir una fila de
     * alguien que ya ejerció su derecho al olvido.
     */
    fun isCoach(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean
}
