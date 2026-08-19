package com.runcriticon.auditoria.application.usecases.events

import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventFilter
import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.domain.AuditEventId
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.auditoria.domain.AuditoriaError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class ListAuditEventsQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val evento =
            AuditEvent(
                id = AuditEventId.new(),
                clubId = club,
                type = AuditEventType.ACCESO_DENEGADO,
                actorId = UUID.randomUUID(),
                sujetoId = null,
                recurso = "PLAN:PUBLISH",
                motivo = "RBAC",
                occurredAt = Instant.now(),
            )

        test("ADMIN puede listar los eventos de auditoria de su club") {
            val repository = InMemoryAuditEventRepository(listOf(evento))
            val query = ListAuditEventsQuery(repository)

            val result = query.execute(admin, AuditEventFilter()).shouldBeRight()

            result shouldBe listOf(evento)
            repository.searches.single().first shouldBe club
        }

        test("ENTRENADOR no puede consultar el log de auditoria") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
            val repository = InMemoryAuditEventRepository(listOf(evento))
            val query = ListAuditEventsQuery(repository)

            query.execute(coach, AuditEventFilter()).shouldBeLeft(AuditoriaError.Forbidden)

            repository.searches.size shouldBe 0
        }

        test("ALUMNO no puede consultar el log de auditoria") {
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)
            val repository = InMemoryAuditEventRepository(listOf(evento))
            val query = ListAuditEventsQuery(repository)

            query.execute(student, AuditEventFilter()).shouldBeLeft(AuditoriaError.Forbidden)
        }

        test("desde posterior a hasta es InvalidInput y no llega al repositorio") {
            val repository = InMemoryAuditEventRepository(listOf(evento))
            val query = ListAuditEventsQuery(repository)
            val filter =
                AuditEventFilter(
                    desde = Instant.parse("2026-08-19T00:00:00Z"),
                    hasta = Instant.parse("2026-08-01T00:00:00Z"),
                )

            val error = query.execute(admin, filter).shouldBeLeft()

            error.shouldBe(AuditoriaError.InvalidInput("desde", "no puede ser posterior a hasta"))
            repository.searches.size shouldBe 0
        }
    })
