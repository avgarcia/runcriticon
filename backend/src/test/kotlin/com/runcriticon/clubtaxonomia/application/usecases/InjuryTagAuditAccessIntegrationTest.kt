package com.runcriticon.clubtaxonomia.application.usecases

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.usecases.students.ListStudentsQuery
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ListStudentTagsQuery
import com.runcriticon.shared.api.events.AccesoADatosSensibles
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * Contra Postgres real: que las lecturas del tag `estado = lesión` lleven `@AuditAccess` no basta, el proxy AOP tiene
 * que envolver el bean y el `AccesoADatosSensibles` tiene que llegar al outbox — mismo motivo que
 * `ListCoachAlertsQueryAuditAccessIntegrationTest`. Qué sujetos se eligen en cada lectura lo cubre
 * `InjuryTagAuditTest`.
 *
 * Club propio por test, con su propio eje `estado`: el club canónico de la siembra lo comparte toda la JVM de tests.
 */
class InjuryTagAuditAccessIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var listStudents: ListStudentsQuery

    @Autowired private lateinit var listStudentTags: ListStudentTagsQuery

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val entrenador = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
    private val lesionado = UuidCreator.getTimeOrderedEpoch()
    private val sano = UuidCreator.getTimeOrderedEpoch()
    private var lesion: UUID = UUID.randomUUID()

    @BeforeEach
    fun prepara() {
        val estado = sembrarEje("estado")
        lesion = sembrarValor(estado, "lesión", datoSalud = true, rol = "LESION")
        val activo = sembrarValor(estado, "activo")
        sembrarAlumno(lesionado, lesion)
        sembrarAlumno(sano, activo)
        autenticar(entrenador)
    }

    @AfterEach
    fun limpia() {
        SecurityContextHolder.clearContext()
        // `event_publication` es compartida por toda la JVM de tests: se borra solo lo de los alumnos de este test.
        listOf(lesionado, sano).forEach {
            jdbc.update(
                "DELETE FROM event_publication WHERE event_type LIKE '%AccesoADatosSensibles' " +
                    "AND serialized_event LIKE ?",
                "%$it%",
            )
        }
    }

    @Test
    fun `filtrar alumnos por lesion deja un acceso auditado por cada lesionado y ninguno para el resto`() {
        listStudents
            .execute(entrenador, listOf(lesion))
            .shouldBeRight()
            .students
            .map { it.id.value } shouldBe
            listOf(lesionado)

        accesosAuditados(lesionado) shouldBe listOf(AccesoADatosSensibles::class.java.name)
        accesosAuditados(sano).shouldBeEmpty()
    }

    @Test
    fun `consultar los tags de un alumno sin lesion no deja acceso auditado`() {
        listStudentTags.execute(entrenador, sano).shouldBeRight()

        accesosAuditados(sano).shouldBeEmpty()
    }

    @Test
    fun `consultar los tags de un alumno lesionado deja un acceso auditado`() {
        listStudentTags.execute(entrenador, lesionado).shouldBeRight()

        accesosAuditados(lesionado) shouldBe listOf(AccesoADatosSensibles::class.java.name)
    }

    private fun accesosAuditados(alumno: UUID): List<String?> =
        jdbc.queryForList(
            "SELECT event_type FROM event_publication WHERE event_type LIKE '%AccesoADatosSensibles' " +
                "AND serialized_event LIKE ?",
            String::class.java,
            "%$alumno%",
        )

    private fun sembrarAlumno(
        id: UUID,
        valor: UUID,
    ) {
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, 'ALUMNO', 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            "Alumno $id",
            "alumno-$id@club.test",
            UuidCreator.getTimeOrderedEpoch(),
        )
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            club.value,
            id,
            valor,
        )
    }

    private fun sembrarEje(nombre: String): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        return id
    }

    private fun sembrarValor(
        keyId: UUID,
        nombre: String,
        datoSalud: Boolean = false,
        rol: String? = null,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre, dato_salud, rol_reservado) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            id,
            keyId,
            club.value,
            nombre,
            datoSalud,
            rol,
        )
        return id
    }

    private fun autenticar(principal: Principal) {
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
