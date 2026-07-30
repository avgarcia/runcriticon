package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonRole
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.api.events.EntrenadorInvitado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Mantiene la proyección local de personas del club a partir de los cuatro eventos de integración que publica
 * `identidad`. Es la única vía por la que este módulo conoce a los alumnos y entrenadores: no hay llamada síncrona a
 * `identidad`, y el alta de personas sigue siendo suya.
 *
 * `@ApplicationModuleListener` aporta las tres propiedades de las que depende este listener: se ejecuta **tras el
 * commit** del caso de uso que publicó el evento, de forma **asíncrona**, y dentro de una **transacción propia**. Esa
 * transacción es la que hace que la marca de idempotencia y la escritura de la proyección caigan juntas: o las dos, o
 * ninguna (ver [ProcessedEventTracker]).
 *
 * Dos protecciones distintas, contra dos problemas distintos:
 *
 *  - **Reentrega del mismo evento** (reintento del outbox): la corta [ProcessedEventTracker] por `event_id`.
 *  - **Eventos desordenados** de la misma persona: no la corta el tracker —son `event_id` distintos, los dos nuevos—
 *    sino la guarda de orden de [PersonProjection.upsert] por `occurredAt`.
 */
@Component
class PersonProjectionListener(
    private val projection: PersonProjection,
    // Qualifier por el literal, no por la constante del adaptador: importarla obligaría a esta clase de `application`
    // a depender de `infrastructure`, que es la dirección prohibida (lo verifica CapasArchTest). Si el literal
    // divergiera del que declara el adaptador, el contexto no arrancaría — el error sería inmediato, no silencioso.
    @Qualifier("clubTaxonomiaProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Siembra el alumno recién invitado, todavía sin activar su cuenta. */
    @ApplicationModuleListener
    fun on(event: AlumnoInvitado) = project(event, event.name, event.email, PersonRole.ALUMNO, PersonStatus.INVITADO)

    /** Pasa el alumno a activo cuando consume su invitación. */
    @ApplicationModuleListener
    fun on(event: AlumnoActivado) = project(event, event.name, event.email, PersonRole.ALUMNO, PersonStatus.ACTIVO)

    /** Siembra el entrenador recién invitado, todavía sin activar su cuenta. */
    @ApplicationModuleListener
    fun on(event: EntrenadorInvitado) =
        project(event, event.name, event.email, PersonRole.ENTRENADOR, PersonStatus.INVITADO)

    /** Pasa el entrenador a activo cuando consume su invitación. */
    @ApplicationModuleListener
    fun on(event: EntrenadorActivado) =
        project(event, event.name, event.email, PersonRole.ENTRENADOR, PersonStatus.ACTIVO)

    /**
     * Cuerpo común a los cuatro eventos. El `id` de la persona es el `aggregateId` del evento, que es el id del usuario
     * en `identidad`: este módulo no genera identidades ajenas.
     */
    private fun project(
        event: IntegrationEvent,
        name: String,
        email: String,
        role: PersonRole,
        status: PersonStatus,
    ) {
        // `module` es el módulo que **procesa** el evento, no el que lo publicó: los logs de este listener son de
        // club_taxonomia, y atribuirlos a identidad rompería el filtrado por módulo en los cuadros de mando.
        mdcRestorer.restore(
            module = MODULE,
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", event.eventId, LISTENER)
                return
            }
            val person =
                Person(
                    id = PersonId.of(event.aggregateId),
                    clubId = ClubId.of(event.clubId),
                    name = name,
                    email = email,
                    role = role,
                    status = status,
                )
            if (!projection.upsert(person, event.eventId, event.occurredAt)) {
                log.info(
                    "Evento {} descartado por la guarda de orden: la proyección de la persona {} ya recogía un " +
                        "evento más reciente",
                    event.eventId,
                    event.aggregateId,
                )
            }
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        /** Tag `module` del MDC: el esquema SQL del módulo, no su paquete Kotlin. */
        const val MODULE = "club_taxonomia"

        /** Clave de idempotencia en `evento_procesado`. Cambiarla reprocesaría todo el histórico de eventos. */
        const val LISTENER = "PersonProjectionListener"
    }
}
