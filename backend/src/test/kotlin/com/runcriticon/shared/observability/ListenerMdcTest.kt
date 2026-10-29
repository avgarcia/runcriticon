package com.runcriticon.shared.observability

import com.runcriticon.auditoria.application.listeners.AuditEventListener
import com.runcriticon.auditoria.application.listeners.AuditTrailAnonymizationListener
import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.EntrenadorEliminadoDeGrupo
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.listeners.LesionDeclaradaListener
import com.runcriticon.clubtaxonomia.application.listeners.MergeSuggestionListener
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
import com.runcriticon.seguimiento.application.listeners.CoachGroupProjectionListener
import com.runcriticon.seguimiento.application.listeners.ConsentProjectionListener
import com.runcriticon.seguimiento.application.listeners.PersonalizationProjectionListener
import com.runcriticon.shared.api.events.AccesoADatosSensibles
import com.runcriticon.shared.api.events.LesionDeclarada
import com.runcriticon.testing.ConstantUserIdHasher
import com.runcriticon.testing.InMemoryProcessedEventTracker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.slf4j.MDC
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * El MDC que cada listener deja durante el procesado y su limpieza al terminar, para los listeners que no lo
 * comprobaban en su propio test. `EventosYListenersArchTest` ya exige que llamen a `restore` y `clear`; esto prueba lo
 * que ArchUnit no ve: los valores (el módulo que consume, no el que publicó) y que `clear` vaya en el `finally`.
 *
 * El MDC se lee en [InMemoryProcessedEventTracker.markIfNew], que todo listener llama justo después de restaurarlo.
 * Con el evento dado por ya procesado, el listener sale sin tocar ningún puerto: por eso basta con mocks vacíos.
 */
class ListenerMdcTest :
    FunSpec({
        afterEach { MDC.clear() }

        for (case in cases) {
            test(
                "${case.name}: durante el procesado el MDC lleva modulo ${case.module}, trace, club y actor",
            ) {
                val tracker = InMemoryProcessedEventTracker().apply { treatAllAsProcessed = true }

                case.deliver(tracker)

                tracker.mdcAtMark shouldBe
                    mapOf(
                        "module" to case.module,
                        "trace_id" to TRACE_ID,
                        "club_id" to CLUB.toString(),
                        "user_id_hash" to ConstantUserIdHasher.HASH,
                    )
                MDC.getCopyOfContextMap().orEmpty().shouldBeEmpty()
            }

            test("${case.name}: si el procesado falla el MDC queda limpio") {
                val tracker =
                    InMemoryProcessedEventTracker().apply {
                        failure =
                            IllegalStateException(
                                "fallo simulado",
                            )
                    }

                shouldThrow<IllegalStateException> { case.deliver(tracker) }

                MDC.getCopyOfContextMap().orEmpty().shouldBeEmpty()
            }
        }
    })

private class Case(
    val name: String,
    val module: String,
    val deliver: (InMemoryProcessedEventTracker) -> Unit,
)

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val TRACEPARENT = "00-$TRACE_ID-b7ad6b7169203331-01"
private val CLUB: UUID = UUID.randomUUID()
private val ACTOR: UUID = UUID.randomUUID()
private val NOW: Instant = Instant.parse("2026-10-29T10:00:00Z")
private val mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher)

private val cases =
    listOf(
        Case("MergeSuggestionListener", "club_taxonomia") { tracker ->
            MergeSuggestionListener(
                groupRepository = mockk(),
                suggestionRepository = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
                clock = Clock.systemUTC(),
                metrics = mockk(),
            ).on(
                MembresiaDeGrupoCambiada(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                    alumnos = emptyList(),
                ),
            )
        },
        Case("LesionDeclaradaListener", "club_taxonomia") { tracker ->
            LesionDeclaradaListener(
                classification = mockk(),
                studentTags = mockk(),
                principalScope = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
            ).on(
                LesionDeclarada(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                ),
            )
        },
        Case("GroupMembersProjectionListener", "planificacion") { tracker ->
            GroupMembersProjectionListener(
                projection = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
            ).on(
                EntrenadorAsignadoAGrupo(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                    groupId = UUID.randomUUID(),
                ),
            )
        },
        Case("PersonalizationProjectionListener", "seguimiento") { tracker ->
            PersonalizationProjectionListener(
                projection = mockk(),
                marks = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
                metrics = mockk(),
            ).on(
                PersonalizacionRetirada(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                    grupoId = UUID.randomUUID(),
                    sesionId = UUID.randomUUID(),
                    dia = LocalDate.parse("2026-10-29"),
                    alumnoId = UUID.randomUUID(),
                    baseSession = PersonalizedSession("RODAJE", null, null, null, null, null, null, null, null),
                ),
            )
        },
        Case("ConsentProjectionListener", "seguimiento") { tracker ->
            ConsentProjectionListener(
                projection = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
            ).on(
                ConsentimientoConcedido(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                    versionTexto = "v2026-08-25",
                ),
            )
        },
        Case("CoachGroupProjectionListener", "seguimiento") { tracker ->
            CoachGroupProjectionListener(
                projection = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
            ).on(
                EntrenadorEliminadoDeGrupo(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                    groupId = UUID.randomUUID(),
                ),
            )
        },
        Case("AuditTrailAnonymizationListener", "auditoria") { tracker ->
            AuditTrailAnonymizationListener(
                repository = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
            ).on(
                EntrenadorEliminado(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                ),
            )
        },
        Case("AuditEventListener", "auditoria") { tracker ->
            AuditEventListener(
                repository = mockk(),
                processedEvents = tracker,
                mdcRestorer = mdcRestorer,
                metrics = mockk(),
            ).on(
                AccesoADatosSensibles(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = NOW,
                    clubId = CLUB,
                    actorId = ACTOR,
                    traceparent = TRACEPARENT,
                    recurso = "TEST:mdc",
                    sujetoId = UUID.randomUUID(),
                ),
            )
        },
    )
