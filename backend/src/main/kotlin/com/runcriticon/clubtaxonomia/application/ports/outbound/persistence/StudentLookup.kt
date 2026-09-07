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

    /**
     * Bloquea de una sola vez a todos los [studentIds] y devuelve cuántos son alumnos vivos de [clubId].
     *
     * @return el tamaño del subconjunto de [studentIds] que existe en el club con rol alumno. El llamador compara
     *   ese número con `studentIds.size`: si no coincide, alguno no existe, es entrenador o es de otro club, y los
     *   tres modos de fallo dan la misma respuesta al cliente. Devolver el conteo y no los ids evita construir una
     *   respuesta que dijera *cuáles* fallan, que sería un enumerador de alumnos ajenos.
     *
     * **Una sola sentencia, no [isStudent] en bucle.** Cincuenta llamadas a [isStudent] tomarían cincuenta bloqueos
     * por persona en el orden en que los mande el cliente; dos operaciones masivas con selecciones solapadas y
     * órdenes distintos se abrazarían. Aquí el bloqueo se toma en un orden determinista por id, que es lo que
     * impide el ciclo.
     *
     * El bloqueo dura hasta el fin de la transacción y sirve para lo mismo que el de [isStudent]: entre esta
     * comprobación y la escritura no cabe una supresión que dejara asignaciones huérfanas de alguien que ya ejerció
     * su derecho al olvido.
     */
    fun lockStudents(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Int
}
