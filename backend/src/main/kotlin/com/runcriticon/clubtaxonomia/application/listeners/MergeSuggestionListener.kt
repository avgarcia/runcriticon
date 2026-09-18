package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.MergeSuggestionMetrics
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionCalculator
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Recalcula las sugerencias de fusión de grupos (LAL-96) cuando cambia la membresía de uno de ellos.
 *
 * **Consume el evento que el propio módulo publica** — a diferencia del resto de listeners de `club_taxonomia`,
 * que consumen eventos de otros módulos. Es deliberado: es la vía que exige el AC4 ("el cálculo no penaliza la
 * carga de la pantalla de grupos, se ejecuta aparte") sin introducir el primer `@Scheduled` del repo. Al ir por
 * el outbox, `@ApplicationModuleListener` aporta las mismas tres propiedades que en cualquier otro consumidor:
 * se ejecuta **tras el commit** que publicó [MembresiaDeGrupoCambiada], de forma **asíncrona** y en una
 * **transacción propia** — así que nunca compite con la petición que creó el grupo o tocó sus tags.
 *
 * Recalcula dos cosas por cada evento:
 *  - **MICRO** ([MergeSuggestionCalculator.isMicro]): solo mira el grupo del propio evento.
 *  - **DUPLICADO** ([MergeSuggestionCalculator.isDuplicate]): compara el grupo del evento contra **todos** los
 *    demás grupos del club ([GroupRepository.resolveAllMembers]) — a la escala prevista (un club, un par de
 *    cientos de grupos como mucho) es una consulta y un bucle en memoria, no *n* consultas.
 *
 * Upsert cuando la condición se cumple, borrado cuando deja de cumplirse — nunca al revés en el mismo lado:
 * [MergeSuggestionRepository.upsert] no toca un descarte ya registrado (AC3), y [MergeSuggestionRepository.delete]
 * es la única vía por la que una sugerencia desaparece antes de que alguien la descarte a mano.
 */
@Component
class MergeSuggestionListener(
    private val groupRepository: GroupRepository,
    private val suggestionRepository: MergeSuggestionRepository,
    // Qualifier por el literal, no por la constante del adaptador (mismo motivo que PersonProjectionListener):
    // importarla obligaría a esta clase de `application` a depender de `infrastructure`, dirección prohibida
    // que verifica CapasArchTest.
    @Qualifier("clubTaxonomiaProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
    private val clock: Clock,
    private val metrics: MergeSuggestionMetrics,
) {
    @ApplicationModuleListener
    fun on(event: MembresiaDeGrupoCambiada) {
        mdcRestorer.restore(
            module = MODULE,
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                return
            }
            val clubId = ClubId.of(event.clubId)
            val groupId = GroupId.of(event.aggregateId)
            val members = event.alumnos.mapTo(mutableSetOf()) { PersonId.of(it) }
            val now = Instant.now(clock)
            recalculateMicro(clubId, groupId, members, now)
            recalculateDuplicates(clubId, groupId, members, now)
        } finally {
            mdcRestorer.clear()
        }
    }

    private fun recalculateMicro(
        clubId: ClubId,
        groupId: GroupId,
        members: Set<PersonId>,
        now: Instant,
    ) {
        val isMicro = MergeSuggestionCalculator.isMicro(members)
        if (isMicro) {
            suggestionRepository.upsert(clubId, MergeSuggestion.micro(groupId, now))
        } else {
            suggestionRepository.delete(clubId, groupId, groupId, MergeSuggestionType.MICRO)
        }
        metrics.recalculated(MergeSuggestionType.MICRO, created = isMicro)
    }

    private fun recalculateDuplicates(
        clubId: ClubId,
        groupId: GroupId,
        members: Set<PersonId>,
        now: Instant,
    ) {
        val others = groupRepository.resolveAllMembers(clubId) - groupId
        others.forEach { (otherGroupId, otherMembers) ->
            val (a, b) = MergeSuggestion.canonicalPair(groupId, otherGroupId)
            val isDuplicate = MergeSuggestionCalculator.isDuplicate(members, otherMembers)
            if (isDuplicate) {
                suggestionRepository.upsert(clubId, MergeSuggestion.duplicateOf(a, b, now))
            } else {
                suggestionRepository.delete(clubId, a, b, MergeSuggestionType.DUPLICADO)
            }
            metrics.recalculated(MergeSuggestionType.DUPLICADO, created = isDuplicate)
        }
    }

    private companion object {
        const val MODULE = "club_taxonomia"
        const val LISTENER = "MergeSuggestionListener"
    }
}
