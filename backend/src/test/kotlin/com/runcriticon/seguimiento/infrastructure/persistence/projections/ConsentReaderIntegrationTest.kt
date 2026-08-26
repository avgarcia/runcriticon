package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * [ConsentReaderJdbc] contra Postgres real (LAL-128 PR2): fail-closed sin fila, `vigente` refleja la
 * proyección, y el filtro `club_id` no deja ver el consentimiento de un alumno de otro club.
 */
class ConsentReaderIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var reader: ConsentReaderJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `sin fila proyectada, fail-closed`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)

        reader.isGranted(clubId, studentId) shouldBe false
    }

    @Test
    fun `con vigente=true, concedido`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        seedRow(clubId, studentId, granted = true)

        reader.isGranted(clubId, studentId) shouldBe true
    }

    @Test
    fun `con vigente=false, revocado`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        seedRow(clubId, studentId, granted = false)

        reader.isGranted(clubId, studentId) shouldBe false
    }

    @Test
    fun `el consentimiento de un alumno de otro club no cuenta, anti-IDOR`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val otroClub = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        seedRow(otroClub, studentId, granted = true)

        reader.isGranted(clubId, studentId) shouldBe false
    }

    private fun seedRow(
        clubId: ClubId,
        studentId: StudentId,
        granted: Boolean,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.consentimiento_alumno
                (alumno_id, club_id, vigente, version_texto, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'v2026-08-25', ?, now())
            """.trimIndent(),
            studentId.value,
            clubId.value,
            granted,
            UUID.randomUUID(),
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
