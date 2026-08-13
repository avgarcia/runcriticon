package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.PersonId

/**
 * Puerto de borrado físico de todo lo que este módulo guarda sobre una persona, al ejercer ella su derecho de
 * supresión. Lo invoca el listener que consume las bajas publicadas por el módulo de identidad.
 *
 * Separado de [PersonProjection] a propósito: esta operación no mantiene la proyección, la destruye, y alcanza también
 * a las asignaciones de tags, a las excepciones manuales de grupo y a las asignaciones de entrenador a grupo, que no
 * son parte de la proyección sino clasificación y pertenencia del club. Quien borra no necesita saber escribir, y
 * quien escribe no necesita saber borrar.
 */
interface PersonErasure {
    /**
     * Borra la proyección de la persona, sus asignaciones de tags, sus excepciones manuales de pertenencia a grupos y
     * sus asignaciones de entrenador a grupo, y deja la **lápida** que impide que un evento de alta rezagado vuelva a
     * materializarla.
     *
     * Las excepciones de grupo no son un extra: una inclusión manual sobrevive al borrado de la persona si no se
     * limpia, y la resolución de membresía la seguiría devolviendo como miembro por su rama de inclusiones. Lo
     * mismo con las asignaciones de entrenador: un entrenador suprimido que siguiera "llevando" un grupo aparecería
     * en la carga de un entrenador que ya no existe.
     *
     * Es idempotente: repetirlo sobre alguien ya borrado no falla y deja el mismo estado. También es válido sobre una
     * persona que este módulo nunca llegó a proyectar —el borrado pudo adelantar al alta—, y ahí deja igualmente la
     * lápida, que es justo lo que hace falta.
     *
     * @return cuántas filas se borraron de cada sitio, para poder registrarlo sin volver a consultar.
     */
    fun erase(personId: PersonId): ErasedRows
}

/** Recuento de lo borrado para una persona. */
data class ErasedRows(
    val projections: Int,
    val tagAssignments: Int,
    val groupOverrides: Int,
    val groupCoachAssignments: Int,
)
