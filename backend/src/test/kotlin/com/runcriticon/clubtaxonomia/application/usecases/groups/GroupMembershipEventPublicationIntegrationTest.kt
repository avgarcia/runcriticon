package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.AssignStudentTagCommand
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ReplaceStudentTagsCommand
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.UnassignStudentTagCommand
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
 * `MembresiaDeGrupoCambiada` publicado de verdad contra Postgres real, en los seis puntos de emisión (LAL-25,
 * prerrequisito). Es el test que justifica el ticket: antes de este cambio, asignar un tag a un alumno no metía a
 * nadie en ningún grupo -- `resolveMembers` daba el resultado correcto, pero nada lo publicaba.
 */
@RecordApplicationEvents
class GroupMembershipEventPublicationIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var createGroup: CreateGroupCommand

    @Autowired private lateinit var overrideMembership: OverrideGroupMembershipCommand

    @Autowired private lateinit var clearOverride: ClearGroupMembershipOverrideCommand

    @Autowired private lateinit var assignTag: AssignStudentTagCommand

    @Autowired private lateinit var unassignTag: UnassignStudentTagCommand

    @Autowired private lateinit var replaceTags: ReplaceStudentTagsCommand

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
    fun `crear un grupo con un filtro que ya cumple un alumno publica su membresia inicial`(events: ApplicationEvents) {
        val valor = sembrarValor("nivel", "medio")
        val alumno = sembrarPersona("ALUMNO")
        asignarTagCrudo(alumno, valor)

        val group = createGroup.execute(admin, "Con filtro", listOf(valor)).shouldBeRight()

        eventosDe(events).single().let {
            it.aggregateId shouldBe group.id.value
            it.alumnos shouldBe listOf(alumno)
        }
    }

    @Test
    fun `un override que mete a un alumno publica el snapshot con el`(events: ApplicationEvents) {
        val grupo = crearGrupo("Sin filtro")
        val alumno = sembrarPersona("ALUMNO")

        overrideMembership.execute(admin, grupo.value, alumno, included = true).shouldBeRight()

        eventosDe(events).single().alumnos shouldBe listOf(alumno)
    }

    @Test
    fun `quitar un override publica el snapshot resultante, aunque antes no publicara nada`(events: ApplicationEvents) {
        val grupo = crearGrupo("Sin filtro")
        val alumno = sembrarPersona("ALUMNO")
        overrideMembership.execute(admin, grupo.value, alumno, included = true).shouldBeRight()

        clearOverride.execute(admin, grupo.value, alumno).shouldBeRight()

        eventosDe(events).last().alumnos.shouldBeEmpty()
    }

    @Test
    fun `asignar un tag que completa el filtro de un grupo publica al alumno dentro`(events: ApplicationEvents) {
        val valor = sembrarValor("nivel", "medio")
        val grupo = crearGrupoConFiltro("Nivel medio", valor)
        val alumno = sembrarPersona("ALUMNO")

        assignTag.execute(admin, alumno, valor).shouldBeRight()

        eventosDe(events).single().let {
            it.aggregateId shouldBe grupo.value
            it.alumnos shouldBe listOf(alumno)
        }
    }

    @Test
    fun `asignar un tag que no esta en ningun filtro no publica nada`(events: ApplicationEvents) {
        sembrarValor("nivel", "medio")
        val otroValor = sembrarValor("terreno", "trail")
        val alumno = sembrarPersona("ALUMNO")

        assignTag.execute(admin, alumno, otroValor).shouldBeRight()

        eventosDe(events).shouldBeEmpty()
    }

    @Test
    fun `quitar un tag que saca al alumno del filtro publica el grupo sin el`(events: ApplicationEvents) {
        val valor = sembrarValor("nivel", "medio")
        val grupo = crearGrupoConFiltro("Nivel medio", valor)
        val alumno = sembrarPersona("ALUMNO")
        asignarTagCrudo(alumno, valor)

        unassignTag.execute(admin, alumno, valor).shouldBeRight()

        val evento = eventosDe(events).single()
        evento.aggregateId shouldBe grupo.value
        evento.alumnos.shouldBeEmpty()
    }

    @Test
    fun `reemplazar los tags recalcula todos los grupos afectados por el delta`(events: ApplicationEvents) {
        val nivel = sembrarValor("nivel", "medio")
        val objetivo = sembrarValor("objetivo", "maraton")
        val grupoNivel = crearGrupoConFiltro("Nivel medio", nivel)
        val grupoObjetivo = crearGrupoConFiltro("Maratón", objetivo)
        val alumno = sembrarPersona("ALUMNO")
        asignarTagCrudo(alumno, nivel)

        // Quita `nivel`, añade `objetivo`: el delta toca los dos grupos, cada uno recibe su snapshot.
        replaceTags.execute(admin, alumno, listOf(objetivo)).shouldBeRight()

        val publicados = eventosDe(events)
        publicados.map { it.aggregateId }.toSet() shouldContainExactlyInAnyOrder
            listOf(grupoNivel.value, grupoObjetivo.value)
        publicados.single { it.aggregateId == grupoNivel.value }.alumnos.shouldBeEmpty()
        publicados.single { it.aggregateId == grupoObjetivo.value }.alumnos shouldBe listOf(alumno)
    }

    private fun eventosDe(events: ApplicationEvents): List<MembresiaDeGrupoCambiada> =
        events.stream(MembresiaDeGrupoCambiada::class.java).toList()

    private fun crearGrupo(nombre: String): GroupId = crearGrupoConFiltro(nombre, requiredValue = null)

    private fun crearGrupoConFiltro(
        nombre: String,
        requiredValue: UUID?,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        requiredValue?.let {
            jdbc.update(
                "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)",
                id,
                club.value,
                it,
            )
        }
        return GroupId.of(id)
    }

    private fun sembrarPersona(rol: String): UUID {
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
    }
}
