package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.NotDoneReason
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Round-trip de [ResolvedPlanReaderJdbc] contra Postgres real: el JSONB de `sesion_resuelta`, las columnas de
 * ritmo planas, el filtro por club/alumno y el desempate cuando el alumno pertenece a dos grupos que resuelven
 * el mismo día (ver el comentario de `FIND_WEEK_SQL`).
 *
 * Las filas se siembran con SQL directo, no vía el listener: aísla la lectura de la escritura, igual que
 * `StudentDirectoryIntegrationTest` en `clubtaxonomia`. `findWeek` lleva `@AuthScope(Scope.CLUB)`, así que cada
 * test autentica un `Principal` del mismo club antes de llamarlo — sin sesión HTTP real, el aspecto falla
 * cerrado (`@AuthScope(CLUB) invocado sin principal`).
 */
class ResolvedPlanReaderIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var reader: ResolvedPlanReaderJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `round-trip de tipo, volumen y notas a traves del JSONB`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = LocalDate.parse("2026-08-17"),
            payloadJson = """{"tipo":"RODAJE","volumenTipo":"DISTANCIA","volumenMetros":8000,"notas":"suave"}""",
        )

        val week = reader.findWeek(clubId, studentId, LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-23"))

        val session = week.single()
        session.type shouldBe SessionType.RODAJE
        session.volume shouldBe SessionVolume.Distance(8000)
        session.notes shouldBe "suave"
    }

    @Test
    fun `round-trip del ritmo absoluto por columnas planas`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = LocalDate.parse("2026-08-17"),
            payloadJson = """{"tipo":"TEMPO"}""",
            ritmoTipoOrigen = "ABSOLUTO",
            ritmoCalculado = 240,
        )

        val session =
            reader
                .findWeek(clubId, studentId, LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-23"))
                .single()

        session.pace shouldBe ResolvedPace(secondsPerKm = 240)
    }

    @Test
    fun `round-trip del ritmo relativo sin marca`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = LocalDate.parse("2026-08-17"),
            payloadJson = """{"tipo":"TEMPO"}""",
            ritmoTipoOrigen = "RELATIVO",
            ritmoFaltaMarca = "10K",
        )

        val session =
            reader
                .findWeek(clubId, studentId, LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-23"))
                .single()

        session.pace shouldBe ResolvedPace(missingMark = RaceDistance.TEN_K)
    }

    @Test
    fun `no devuelve filas de otro club ni de otro alumno`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        insertRow(clubId = ClubId.of(UUID.randomUUID()), studentId = studentId, dia = LocalDate.parse("2026-08-17"))
        insertRow(clubId = clubId, studentId = StudentId.of(UUID.randomUUID()), dia = LocalDate.parse("2026-08-17"))

        val week = reader.findWeek(clubId, studentId, LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-23"))

        week.shouldBeEmpty()
    }

    @Test
    fun `dos planes que resuelven el mismo dia se desempatan por el evento mas reciente`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-08-17")
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = dia,
            payloadJson = """{"tipo":"RODAJE"}""",
            occurredAt = Instant.parse("2026-08-13T09:00:00Z"),
        )
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = dia,
            payloadJson = """{"tipo":"TEMPO"}""",
            occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
        )

        val week = reader.findWeek(clubId, studentId, dia, dia)

        week.map { it.type } shouldBe listOf(SessionType.TEMPO)
    }

    @Test
    fun `findDay desempata igual que findWeek, mismo plan_id para el mismo dia`() {
        // Ancla `SubmitSessionReportCommand.execute` (findDay) al mismo plan que ve el alumno en su
        // semana (findWeek) cuando pertenece a dos grupos que resuelven el mismo día — si divergieran,
        // el reporte se guardaría contra un `plan_id` que el JOIN de `findWeek` nunca casa, y
        // desaparecería de la UI en la siguiente carga.
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-08-17")
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = dia,
            payloadJson = """{"tipo":"RODAJE"}""",
            occurredAt = Instant.parse("2026-08-13T09:00:00Z"),
        )
        val planMasReciente =
            insertRow(
                clubId = clubId,
                studentId = studentId,
                dia = dia,
                payloadJson = """{"tipo":"TEMPO"}""",
                occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
            )

        val deLaSemana = reader.findWeek(clubId, studentId, dia, dia).single()
        val delDia = reader.findDay(clubId, studentId, dia)

        deLaSemana.planId.value shouldBe planMasReciente
        delDia?.planId?.value shouldBe planMasReciente
    }

    @Test
    fun `sin reporte todavia, el campo report es null`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        insertRow(clubId = clubId, studentId = studentId, dia = LocalDate.parse("2026-08-17"))

        val session =
            reader
                .findWeek(clubId, studentId, LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-23"))
                .single()

        session.report shouldBe null
    }

    @Test
    fun `un reporte existente se trae por LEFT JOIN sobre la clave natural completa`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-08-17")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = dia)
        insertReport(
            clubId = clubId,
            studentId = studentId,
            planId = planId,
            dia = dia,
            estado = "PARCIAL",
            valoracion = 3,
        )

        val session = reader.findDay(clubId, studentId, dia)

        session?.report?.status shouldBe ReportStatus.PARCIAL
        session?.report?.rating shouldBe 3
    }

    @Test
    fun `un reporte NO_HECHO con motivo MOLESTIAS trae la marca de dolor activa`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-08-17")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = dia)
        insertReport(
            clubId = clubId,
            studentId = studentId,
            planId = planId,
            dia = dia,
            estado = "NO_HECHO",
            motivo = "MOLESTIAS",
            marcaDolor = true,
        )

        val session = reader.findDay(clubId, studentId, dia)

        session?.report?.status shouldBe ReportStatus.NO_HECHO
        session?.report?.reason shouldBe NotDoneReason.MOLESTIAS
        session?.report?.painFlag shouldBe true
        session?.report?.rating shouldBe null
    }

    @Test
    fun `un reporte de otro club no se trae, aunque coincida alumno-plan-dia`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-08-17")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = dia)
        insertReport(
            clubId = ClubId.of(UUID.randomUUID()),
            studentId = studentId,
            planId = planId,
            dia = dia,
            estado = "HECHO",
            valoracion = 5,
        )

        val session = reader.findDay(clubId, studentId, dia)

        session?.report shouldBe null
    }

    /** Inserta una fila de `plan_resuelto_por_alumno` y devuelve el `plan_id` usado (aleatorio si no se
     * indica), para que los tests de reporte puedan anclar su fila a la misma clave natural. */
    private fun insertRow(
        clubId: ClubId,
        studentId: StudentId,
        dia: LocalDate,
        payloadJson: String = """{"tipo":"RODAJE"}""",
        ritmoTipoOrigen: String? = null,
        ritmoCalculado: Int? = null,
        ritmoReferencia: String? = null,
        ritmoFaltaMarca: String? = null,
        occurredAt: Instant = Instant.parse("2026-08-13T10:00:00Z"),
        planId: UUID = UUID.randomUUID(),
    ): UUID {
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, ritmo_tipo_origen, ritmo_calculado_seg_por_km,
                 ritmo_referencia_distancia, ritmo_falta_marca, mensaje_al_alumno, es_personalizada,
                 last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, NULL, FALSE, ?, ?)
            """.trimIndent(),
            studentId.value,
            planId,
            clubId.value,
            dia,
            payloadJson,
            ritmoTipoOrigen,
            ritmoCalculado,
            ritmoReferencia,
            ritmoFaltaMarca,
            UUID.randomUUID(),
            Timestamp.from(occurredAt),
        )
        return planId
    }

    private fun insertReport(
        clubId: ClubId,
        studentId: StudentId,
        planId: UUID,
        dia: LocalDate,
        estado: String,
        valoracion: Int? = null,
        motivo: String? = null,
        marcaDolor: Boolean = false,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.reporte_sesion
                (alumno_id, plan_id, dia, club_id, estado, valoracion, motivo, marca_dolor, reportado_en,
                 actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """.trimIndent(),
            studentId.value,
            planId,
            dia,
            clubId.value,
            estado,
            valoracion,
            motivo,
            marcaDolor,
        )
    }

    private fun autenticar(
        clubId: ClubId,
        studentId: StudentId,
    ) {
        val principal = Principal(userId = studentId.value, clubId = clubId.value, role = Role.ALUMNO)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }
}
