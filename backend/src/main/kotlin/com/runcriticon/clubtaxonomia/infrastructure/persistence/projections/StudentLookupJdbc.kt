package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Adaptador de [StudentLookup] sobre la proyección local de personas.
 *
 * **El bloqueo no es una optimización, es la corrección.** La supresión de una persona
 * ([com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure]) borra su fila de `persona` y
 * sus asignaciones en la misma transacción. Sin bloqueo:
 *
 * ```
 * clasificar : comprueba que el alumno existe → sí
 * suprimir   : lápida + borra persona + borra alumno_tag + commit
 * clasificar : inserta las asignaciones + commit   ← resucita datos de alguien ya suprimido
 * ```
 *
 * Tomando [lockPerson] **antes** de leer, las dos rutas se serializan para esa persona y la comprobación pasa a ser
 * atómica respecto al borrado. Y entonces basta con mirar `persona`: la supresión siempre borra esa fila, así que un
 * `true` bajo el bloqueo garantiza que no hay supresión commiteada ni en vuelo. De regalo, serializa también dos
 * reemplazos simultáneos del mismo alumno.
 *
 * ⚠️ Hereda la dependencia de [lockPerson] del nivel de aislamiento `READ COMMITTED`: elevarlo rompería la guarda en
 * silencio.
 */
@Repository
class StudentLookupJdbc(
    private val jdbc: JdbcTemplate,
) : StudentLookup {
    @AuthScope(Scope.CLUB)
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean {
        lockPerson(jdbc, personId.value)
        return jdbc.queryForObject(IS_STUDENT_SQL, Boolean::class.java, personId.value, clubId.value) ?: false
    }
}

// SQL a nivel de fichero, no en `companion object`: una propiedad privada del companion leída desde la clase genera un
// accesor sintético público que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.

/**
 * Filtra por club además de por id: el id es la clave primaria, pero sin el club una persona de otro club daría `true`
 * y abriría la puerta a clasificarla.
 */
private const val IS_STUDENT_SQL =
    "SELECT EXISTS(SELECT 1 FROM club_taxonomia.persona WHERE id = ? AND club_id = ? AND rol = 'ALUMNO')"
