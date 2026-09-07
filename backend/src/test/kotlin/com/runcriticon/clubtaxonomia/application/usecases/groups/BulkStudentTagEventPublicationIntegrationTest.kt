package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.AssignStudentTagInBulkCommand
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.UnassignStudentTagInBulkCommand
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.util.UUID

/**
 * `MembresiaDeGrupoCambiada` publicado de verdad contra Postgres real cuando la clasificación toca a muchos alumnos
 * a la vez. Es el test que protege la propiedad central de la clasificación en masa (`BulkStudentClassification`):
 * un evento por grupo afectado, al final, con la membresía completa — no un evento por alumno ni un evento con un
 * snapshot a medio aplicar.
 */
@RecordApplicationEvents
class BulkStudentTagEventPublicationIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var createGroup: CreateGroupCommand

    @Autowired private lateinit var assignInBulk: AssignStudentTagInBulkCommand

    @Autowired private lateinit var unassignInBulk: UnassignStudentTagInBulkCommand

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    @BeforeEach
    fun prepara() {
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `asignar un valor a cincuenta alumnos publica un evento por grupo afectado, con la membresia completa`(
        events: ApplicationEvents,
    ) {
        val valor = sembrarValor("nivel", "medio")
        val grupoUno = crearGrupoConFiltro("Nivel medio A", valor)
        val grupoDos = crearGrupoConFiltro("Nivel medio B", valor)
        val otroValor = sembrarValor("terreno", "trail")
        crearGrupoConFiltro("Trail", otroValor) // grupo no afectado: su filtro no toca el valor asignado
        val alumnos = (1..CINCUENTA).map { sembrarPersona("ALUMNO") }

        val actualizados = assignInBulk.execute(admin, alumnos, valor).shouldBeRight()

        actualizados shouldBe CINCUENTA
        asignados(valor) shouldBe CINCUENTA

        val publicados = eventosDe(events)
        publicados shouldHaveSize 2
        publicados.map { it.aggregateId }.toSet() shouldContainExactlyInAnyOrder listOf(grupoUno.value, grupoDos.value)
        publicados.forEach { it.alumnos shouldContainExactlyInAnyOrder alumnos }

        asientosDeAuditoria() shouldBe CINCUENTA
    }

    @Test
    fun `repetir la misma asignacion en masa no actualiza a nadie ni publica ni audita de nuevo`(
        events: ApplicationEvents,
    ) {
        val valor = sembrarValor("nivel", "medio")
        crearGrupoConFiltro("Nivel medio", valor)
        // Estado inicial sembrado con SQL crudo, no vía classification: así el único evento que puede aparecer en
        // `events` es el de la llamada bajo prueba, no el de un "assign" previo usado solo para preparar el terreno.
        val alumnos = (1..CINCUENTA).map { sembrarPersona("ALUMNO").also { asignarTagCrudo(it, valor) } }

        val actualizados = assignInBulk.execute(admin, alumnos, valor).shouldBeRight()

        actualizados shouldBe 0
        asientosDeAuditoria() shouldBe 0
        eventosDe(events).shouldBeEmpty()
    }

    @Test
    fun `un id de otro club entre cincuenta rechaza la operacion entera, sin filas ni eventos`(
        events: ApplicationEvents,
    ) {
        val valor = sembrarValor("nivel", "medio")
        crearGrupoConFiltro("Nivel medio", valor)
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val ajeno = sembrarPersona("ALUMNO", club = otroClub)
        val alumnos = (1 until CINCUENTA).map { sembrarPersona("ALUMNO") } + ajeno

        assignInBulk.execute(admin, alumnos, valor).shouldBeLeft()

        asignados(valor) shouldBe 0
        eventosDe(events).shouldBeEmpty()
        asientosDeAuditoria() shouldBe 0
    }

    @Test
    fun `quitar un valor a los que lo tenian saca a todos del grupo en un unico evento`(events: ApplicationEvents) {
        val valor = sembrarValor("nivel", "medio")
        val grupo = crearGrupoConFiltro("Nivel medio", valor)
        // Igual que en el test de repetición: sembrado con SQL crudo para que `events` solo recoja lo que publica
        // la llamada bajo prueba.
        val alumnos = (1..CINCUENTA).map { sembrarPersona("ALUMNO").also { asignarTagCrudo(it, valor) } }

        val actualizados = unassignInBulk.execute(admin, alumnos, valor).shouldBeRight()

        actualizados shouldBe CINCUENTA
        asignados(valor) shouldBe 0
        eventosDe(events).single().let {
            it.aggregateId shouldBe grupo.value
            it.alumnos.shouldBeEmpty()
        }
    }

    private fun eventosDe(events: ApplicationEvents): List<MembresiaDeGrupoCambiada> =
        events.stream(MembresiaDeGrupoCambiada::class.java).toList()

    private fun crearGrupoConFiltro(
        nombre: String,
        requiredValue: UUID,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)",
            id,
            club.value,
            requiredValue,
        )
        return GroupId.of(id)
    }

    private fun sembrarPersona(
        rol: String,
        club: ClubId = this.club,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            "Persona $rol",
            "persona-$id@club.test",
            rol,
            UuidCreator.getTimeOrderedEpoch(),
        )
        return id
    }

    private fun sembrarValor(
        eje: String,
        valor: String,
    ): UUID {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        val valueId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            club.value,
            "$eje-${keyId.toString().takeLast(SUFIJO_UNICO)}",
        )
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            valueId,
            keyId,
            club.value,
            valor,
        )
        return valueId
    }

    private fun asignarTagCrudo(
        alumno: UUID,
        valor: UUID,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            club.value,
            alumno,
            valor,
        )
    }

    private fun asignados(valor: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.alumno_tag WHERE club_id = ? AND tag_value_id = ?",
            Int::class.java,
            club.value,
            valor,
        ) ?: 0

    private fun asientosDeAuditoria(): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.evento_auditoria " +
                "WHERE club_id = ? AND tipo = 'TAGS_ALUMNO_ACTUALIZADOS'",
            Int::class.java,
            club.value,
        ) ?: 0

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

    private companion object {
        const val SUFIJO_UNICO = 8
        const val CINCUENTA = 50
    }
}
