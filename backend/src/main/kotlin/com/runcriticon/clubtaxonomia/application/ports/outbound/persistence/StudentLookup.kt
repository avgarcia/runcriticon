package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Comprueba contra la proyección local que una persona del club existe y es alumno, antes de clasificarla.
 *
 * Puerto aparte de [PersonProjection] a propósito, aunque lean la misma tabla: aquel es el puerto de **escritura** de
 * la proyección y sus métodos corren en listeners sin sesión, exentos del filtro de club; este corre dentro de una
 * petición con principal y sí se somete a él. Mezclarlos haría ilegible la justificación de cada exención.
 */
interface StudentLookup {
    /**
     * `true` solo si en [clubId] hay una persona con ese id **y rol alumno**. Devuelve `Boolean` y no la persona
     * porque quien clasifica no necesita su nombre ni su email, y porque los tres modos de fallo —no existe, es
     * entrenador, es de otro club— dan la misma respuesta al cliente.
     *
     * **Toma un bloqueo sobre la persona que dura hasta el fin de la transacción.** Sin él, entre esta comprobación y
     * la escritura de las asignaciones cabría una supresión: el borrado dejaría la tabla limpia y la asignación
     * volvería a escribir filas de alguien que ya ejerció su derecho al olvido.
     */
    fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean
}
