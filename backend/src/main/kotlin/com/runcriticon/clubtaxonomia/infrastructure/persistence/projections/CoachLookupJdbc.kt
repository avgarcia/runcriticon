package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachLookup
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Adaptador de [CoachLookup] sobre la proyección local de personas, simétrico de [StudentLookupJdbc].
 *
 * **El bloqueo no es una optimización, es la corrección** — mismo motivo documentado en [StudentLookupJdbc]: sin
 * [lockPerson] antes de leer, una supresión concurrente del entrenador podría intercalarse entre esta comprobación
 * y la escritura de la asignación, resucitando datos de alguien ya suprimido.
 */
@Repository
class CoachLookupJdbc(
    private val jdbc: JdbcTemplate,
) : CoachLookup {
    @AuthScope(Scope.CLUB)
    override fun isCoach(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean {
        lockPerson(jdbc, personId.value)
        return jdbc.queryForObject(IS_COACH_SQL, Boolean::class.java, personId.value, clubId.value) ?: false
    }
}

// SQL a nivel de fichero, no en `companion object`: una propiedad privada del companion leída desde la clase genera un
// accesor sintético público que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.

/**
 * Filtra por club además de por id: el id es la clave primaria, pero sin el club una persona de otro club daría `true`
 * y abriría la puerta a asignarla a un grupo ajeno.
 */
private const val IS_COACH_SQL =
    "SELECT EXISTS(SELECT 1 FROM club_taxonomia.persona WHERE id = ? AND club_id = ? AND rol = 'ENTRENADOR')"
