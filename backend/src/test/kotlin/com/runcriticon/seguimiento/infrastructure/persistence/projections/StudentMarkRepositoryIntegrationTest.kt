package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

/**
 * [StudentMarkRepositoryJdbc] contra Postgres real (LAL-31): el upsert como edición sobre la PK natural
 * `(alumno_id, distancia)`, el borrado idempotente, el filtro `club_id` de `@AuthScope`, y que los CHECK de
 * `V202608280001__crea_marca_alumno.sql` rechazan lo que `StudentMark.create` ya impide en dominio.
 */
class StudentMarkRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var repository: StudentMarkRepositoryJdbc

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `una segunda marca de la misma distancia edita la fila en vez de duplicarla`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)

        repository.upsert(clubId, studentId, marca(RaceDistance.TEN_K, 2850))
        repository.upsert(clubId, studentId, marca(RaceDistance.TEN_K, 2700))

        val filas = jdbc.queryForObject(COUNT_SQL, Int::class.java, studentId.value, "10K")
        filas shouldBe 1
        val tiempo =
            jdbc.queryForObject(
                "SELECT tiempo_segundos FROM seguimiento.marca_alumno WHERE alumno_id = ? AND distancia = ?",
                Int::class.java,
                studentId.value,
                "10K",
            )
        tiempo shouldBe 2700
    }

    @Test
    fun `dos distancias distintas del mismo alumno son dos filas`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)

        repository.upsert(clubId, studentId, marca(RaceDistance.FIVE_K, 1365))
        repository.upsert(clubId, studentId, marca(RaceDistance.MARATHON, 12600))

        val marcas = repository.findAll(clubId, studentId)
        marcas.size shouldBe 2
        marcas[RaceDistance.FIVE_K]?.timeSeconds shouldBe 1365
        marcas[RaceDistance.MARATHON]?.timeSeconds shouldBe 12600
    }

    @Test
    fun `borrar una marca existente devuelve true y la elimina`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)
        repository.upsert(clubId, studentId, marca(RaceDistance.HALF_MARATHON, 6300))

        val borrada = repository.delete(clubId, studentId, RaceDistance.HALF_MARATHON)

        borrada shouldBe true
        repository.findAll(clubId, studentId).size shouldBe 0
    }

    @Test
    fun `borrar una marca inexistente es idempotente y devuelve false`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val studentId = StudentId.of(UUID.randomUUID())
        autenticar(clubId, studentId)

        val borrada = repository.delete(clubId, studentId, RaceDistance.FIVE_K)

        borrada shouldBe false
    }

    @Test
    fun `el CHECK de distancia rechaza un literal fuera del catalogo, aunque se salte el dominio`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO seguimiento.marca_alumno (alumno_id, distancia, tiempo_segundos, club_id) " +
                    "VALUES (?, 'SPRINT', 100, ?)",
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }
    }

    @Test
    fun `el CHECK de tiempo positivo rechaza cero, aunque se salte el dominio`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO seguimiento.marca_alumno (alumno_id, distancia, tiempo_segundos, club_id) " +
                    "VALUES (?, '5K', 0, ?)",
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }
    }

    private fun marca(
        distance: RaceDistance,
        timeSeconds: Int,
    ): StudentMark = StudentMark(distance, timeSeconds, modifiedAt = Instant.parse("2026-08-28T18:00:00Z"))

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

private const val COUNT_SQL = "SELECT count(*) FROM seguimiento.marca_alumno WHERE alumno_id = ? AND distancia = ?"
