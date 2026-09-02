package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
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

        session.pace shouldBe ResolvedPace.Absolute(240)
    }

    @Test
    fun `round-trip del ritmo relativo sin marca, fila legacy sin delta (LAL-32)`() {
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

        session.pace shouldBe ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = null, secondsPerKm = null)
    }

    @Test
    fun `round-trip del ritmo relativo ya resuelto, con delta y contexto de marca (LAL-32)`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        insertRow(
            clubId = clubId,
            studentId = studentId,
            dia = LocalDate.parse("2026-08-17"),
            payloadJson = """{"tipo":"TEMPO"}""",
            ritmoTipoOrigen = "RELATIVO",
            ritmoCalculado = 250,
            ritmoReferencia = "10K",
            ritmoDelta = 10,
        )

        val session =
            reader
                .findWeek(clubId, studentId, LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-23"))
                .single()

        session.pace shouldBe ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = 250)
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

    @Test
    fun `sin reajuste, el dia efectivo es el planificado y adjustment es null`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-09-02")
        insertRow(clubId = clubId, studentId = studentId, dia = dia)

        val session = reader.findDay(clubId, studentId, dia)

        session?.day shouldBe dia
        session?.plannedDay shouldBe dia
        session?.adjustment shouldBe null
    }

    @Test
    fun `una sesion MOVIDA dentro de la semana aparece en el dia destino, no en el planificado`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val diaPlanificado = LocalDate.parse("2026-09-02")
        val diaDestino = LocalDate.parse("2026-09-04")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = diaPlanificado)
        insertAdjustment(
            clubId = clubId,
            studentId = studentId,
            planId = planId,
            dia = diaPlanificado,
            accion = "MOVIDA",
            diaDestino = diaDestino,
        )

        val week =
            reader.findWeek(clubId, studentId, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"))

        val session = week.single()
        session.day shouldBe diaDestino
        session.plannedDay shouldBe diaPlanificado
        session.adjustment?.action shouldBe AdjustmentAction.MOVIDA
        session.adjustment?.targetDay shouldBe diaDestino
        reader.findDay(clubId, studentId, diaPlanificado) shouldBe null
        reader.findDay(clubId, studentId, diaDestino)?.day shouldBe diaDestino
    }

    @Test
    fun `una sesion MOVIDA fuera del rango consultado desaparece de findWeek`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val diaPlanificado = LocalDate.parse("2026-09-02")
        val diaDestino = LocalDate.parse("2026-09-09")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = diaPlanificado)
        insertAdjustment(
            clubId = clubId,
            studentId = studentId,
            planId = planId,
            dia = diaPlanificado,
            accion = "MOVIDA",
            diaDestino = diaDestino,
        )

        val week =
            reader.findWeek(clubId, studentId, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"))

        week.shouldBeEmpty()
    }

    @Test
    fun `una sesion movida desde fuera de la semana aparece en su dia destino dentro de ella`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val diaPlanificado = LocalDate.parse("2026-08-28")
        val diaDestino = LocalDate.parse("2026-09-02")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = diaPlanificado)
        insertAdjustment(
            clubId = clubId,
            studentId = studentId,
            planId = planId,
            dia = diaPlanificado,
            accion = "MOVIDA",
            diaDestino = diaDestino,
        )

        val week =
            reader.findWeek(clubId, studentId, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"))

        val session = week.single()
        session.day shouldBe diaDestino
        session.plannedDay shouldBe diaPlanificado
    }

    @Test
    fun `una sesion SALTADA se queda en su dia planificado con la marca de saltada`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val dia = LocalDate.parse("2026-09-02")
        val planId = insertRow(clubId = clubId, studentId = studentId, dia = dia)
        insertAdjustment(
            clubId = clubId,
            studentId = studentId,
            planId = planId,
            dia = dia,
            accion = "SALTADA",
            diaDestino = null,
            motivo = "MOLESTIAS",
            marcaDolor = true,
        )

        val session = reader.findDay(clubId, studentId, dia)

        session?.day shouldBe dia
        session?.plannedDay shouldBe dia
        session?.adjustment?.action shouldBe AdjustmentAction.SALTADA
        session?.adjustment?.targetDay shouldBe null
        session?.adjustment?.reason shouldBe AdjustmentReason.MOLESTIAS
        session?.adjustment?.painFlag shouldBe true
    }

    @Test
    fun `un intercambio deja dos sesiones de planes distintos en el dia efectivo de la otra`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        val diaA = LocalDate.parse("2026-09-02")
        val diaB = LocalDate.parse("2026-09-04")
        val planA = insertRow(clubId = clubId, studentId = studentId, dia = diaA, payloadJson = """{"tipo":"RODAJE"}""")
        val planB = insertRow(clubId = clubId, studentId = studentId, dia = diaB, payloadJson = """{"tipo":"SERIES"}""")
        val operacionId = UUID.randomUUID()
        insertAdjustment(clubId, studentId, planA, diaA, "MOVIDA", diaB, operacionId = operacionId)
        insertAdjustment(clubId, studentId, planB, diaB, "MOVIDA", diaA, operacionId = operacionId)

        val enDiaA = reader.findDay(clubId, studentId, diaA)
        val enDiaB = reader.findDay(clubId, studentId, diaB)

        enDiaA?.type shouldBe SessionType.SERIES
        enDiaA?.plannedDay shouldBe diaB
        enDiaB?.type shouldBe SessionType.RODAJE
        enDiaB?.plannedDay shouldBe diaA
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
        ritmoDelta: Int? = null,
        occurredAt: Instant = Instant.parse("2026-08-13T10:00:00Z"),
        planId: UUID = UUID.randomUUID(),
    ): UUID {
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, ritmo_tipo_origen, ritmo_calculado_seg_por_km,
                 ritmo_referencia_distancia, ritmo_falta_marca, ritmo_delta_seg_por_km, mensaje_al_alumno,
                 es_personalizada, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, NULL, FALSE, ?, ?)
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
            ritmoDelta,
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

    private fun insertAdjustment(
        clubId: ClubId,
        studentId: StudentId,
        planId: UUID,
        dia: LocalDate,
        accion: String,
        diaDestino: LocalDate?,
        motivo: String = "CANSANCIO",
        marcaDolor: Boolean = false,
        operacionId: UUID = UUID.randomUUID(),
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.reajuste_dia
                (alumno_id, plan_id, dia, club_id, operacion_id, accion, dia_destino, motivo, marca_dolor,
                 creado_en, actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """.trimIndent(),
            studentId.value,
            planId,
            dia,
            clubId.value,
            operacionId,
            accion,
            diaDestino,
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
