package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.GroupId
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
 * Round-trip de [ClubHealthReaderJdbc] contra Postgres real: el `MAX` por grupo, el caso borde
 * `grupo_id IS NULL` (filas de `plan_resuelto_por_alumno` proyectadas antes de la migración que añadió la
 * columna), el aislamiento por club y que un mismo alumno cuenta en cada uno de sus grupos.
 *
 * Las filas se siembran con SQL directo, no vía los listeners: aísla la lectura de la escritura, mismo
 * criterio que [CoachAlertReaderJdbcIntegrationTest].
 */
class ClubHealthReaderJdbcIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var reader: ClubHealthReaderJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `dos reportes del mismo grupo devuelven una sola fila con el mas reciente`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        autenticar(clubId)
        val antiguo = seedReport(clubId, groupId, dia = LocalDate.parse("2026-09-01"), reportadoEn = INSTANTE_BASE)
        val reciente =
            seedReport(
                clubId,
                groupId,
                dia = LocalDate.parse("2026-09-05"),
                reportadoEn = INSTANTE_BASE.plusSeconds(SEGUNDOS_UN_DIA),
            )
        require(antiguo < reciente)

        val actividad = reader.findLastActivityByGroup(clubId)

        actividad.size shouldBe 1
        actividad.single().groupId shouldBe groupId
        actividad.single().lastReportedAt shouldBe reciente
    }

    @Test
    fun `dos grupos con actividad devuelven dos filas cada una con su propio maximo`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val groupA = GroupId.of(UUID.randomUUID())
        val groupB = GroupId.of(UUID.randomUUID())
        autenticar(clubId)
        val maximoA = seedReport(clubId, groupA, dia = LocalDate.parse("2026-09-01"), reportadoEn = INSTANTE_BASE)
        val maximoB =
            seedReport(
                clubId,
                groupB,
                dia = LocalDate.parse("2026-09-02"),
                reportadoEn = INSTANTE_BASE.plusSeconds(SEGUNDOS_UN_DIA),
            )

        val actividad = reader.findLastActivityByGroup(clubId)

        actividad.single { it.groupId == groupA }.lastReportedAt shouldBe maximoA
        actividad.single { it.groupId == groupB }.lastReportedAt shouldBe maximoB
    }

    @Test
    fun `un grupo sin ningun reporte no aparece en el resultado`() {
        val clubId = ClubId.of(UUID.randomUUID())
        autenticar(clubId)

        reader.findLastActivityByGroup(clubId).shouldBeEmpty()
    }

    @Test
    fun `una fila con grupo_id NULL (legacy, pre-migracion) queda excluida del agregado`() {
        val clubId = ClubId.of(UUID.randomUUID())
        autenticar(clubId)
        seedReport(clubId, groupId = null, dia = LocalDate.parse("2026-09-01"), reportadoEn = INSTANTE_BASE)

        reader.findLastActivityByGroup(clubId).shouldBeEmpty()
    }

    @Test
    fun `reportes de otro club no se cuelan en el agregado`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val otroClub = ClubId.of(UUID.randomUUID())
        val groupId = GroupId.of(UUID.randomUUID())
        autenticar(clubId)
        seedReport(otroClub, groupId, dia = LocalDate.parse("2026-09-01"), reportadoEn = INSTANTE_BASE)

        reader.findLastActivityByGroup(clubId).shouldBeEmpty()
    }

    @Test
    fun `un alumno en dos grupos distintos cuenta en el agregado de cada uno`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        val groupA = GroupId.of(UUID.randomUUID())
        val groupB = GroupId.of(UUID.randomUUID())
        autenticar(clubId)
        val enA =
            seedReport(
                clubId,
                groupA,
                dia = LocalDate.parse("2026-09-01"),
                reportadoEn = INSTANTE_BASE,
                studentId = studentId,
            )
        val enB =
            seedReport(
                clubId,
                groupB,
                dia = LocalDate.parse("2026-09-02"),
                reportadoEn = INSTANTE_BASE.plusSeconds(SEGUNDOS_UN_DIA),
                studentId = studentId,
            )

        val actividad = reader.findLastActivityByGroup(clubId)

        actividad.single { it.groupId == groupA }.lastReportedAt shouldBe enA
        actividad.single { it.groupId == groupB }.lastReportedAt shouldBe enB
    }

    private fun seedReport(
        clubId: ClubId,
        groupId: GroupId?,
        dia: LocalDate,
        reportadoEn: Instant,
        studentId: StudentId = StudentId.of(UUID.randomUUID()),
    ): Instant {
        val planId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, grupo_id, dia, sesion_resuelta, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
            studentId.value,
            planId,
            clubId.value,
            groupId?.value,
            dia,
            """{"tipo":"RODAJE"}""",
            UUID.randomUUID(),
            Timestamp.from(Instant.now()),
        )
        jdbc.update(
            """
            INSERT INTO seguimiento.reporte_sesion (alumno_id, plan_id, dia, club_id, estado, valoracion, reportado_en)
            VALUES (?, ?, ?, ?, 'HECHO', 3, ?)
            """.trimIndent(),
            studentId.value,
            planId,
            dia,
            clubId.value,
            Timestamp.from(reportadoEn),
        )
        return reportadoEn
    }

    private fun autenticar(clubId: ClubId) {
        val principal = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    private companion object {
        val INSTANTE_BASE: Instant = Instant.parse("2026-09-01T10:00:00Z")
        const val SEGUNDOS_UN_DIA = 86_400L
    }
}
