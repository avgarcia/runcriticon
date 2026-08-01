package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: se han eliminado un alumno y sus datos personales del club, en ejercicio del derecho de
 * supresión. Lo publica el caso de uso
 * [com.runcriticon.identidad.application.usecases.account.DeleteUserCommand] dentro de su transacción; los módulos que
 * mantienen proyecciones locales del alumno lo consumen para borrar físicamente sus filas.
 *
 * **Sin `name` ni `email`**, a diferencia del resto de eventos de este módulo. Es deliberado: el payload de un evento
 * vive en el outbox mucho después de publicarse, así que propagar aquí la PII la dejaría escrita justo cuando se acaba
 * de borrar el original. El consumidor no la necesita — le basta el [aggregateId] para saber a quién borrar.
 *
 * Schema versionado en `schemas/identidad/alumno-eliminado-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class AlumnoEliminado(
    override val eventId: UUID,
    /** Identificador del alumno eliminado; es su antiguo id de usuario. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : IntegrationEvent
