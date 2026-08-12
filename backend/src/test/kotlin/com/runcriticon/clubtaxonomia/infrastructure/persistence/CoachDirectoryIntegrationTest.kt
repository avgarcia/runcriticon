package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachDirectory
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
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
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * El listado de entrenadores contra Postgres real: filtro por rol y por club viven en SQL, no en el dominio.
 */
class CoachDirectoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var directory: CoachDirectory

    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    // Club propio por test, nunca el de bootstrap: el contenedor Postgres es único para toda la JVM.
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
    fun `devuelve solo a los entrenadores del club, no a los alumnos`() {
        val carlos = sembrarPersona("ENTRENADOR", nombre = "Carlos Ruiz")
        sembrarPersona("ALUMNO", nombre = "Ana Ruiz")

        listar().map { it.id } shouldBe listOf(carlos)
    }

    @Test
    fun `sin entrenadores la lista sale vacia, no un error`() {
        listar().shouldBeEmpty()
    }

    @Test
    fun `sin asignacion de grupos, groups y totalStudents salen vacios`() {
        sembrarPersona("ENTRENADOR")

        val resultado = listar().single()

        resultado.groups.shouldBeEmpty()
        resultado.totalStudents shouldBe 0
    }

    @Test
    fun `un grupo asignado y con miembros aparece con su nombre y su recuento`() {
        val entrenador = sembrarPersona("ENTRENADOR")
        val alumno = sembrarPersona("ALUMNO")
        val nivelMedio = sembrarValor("nivel", "medio")
        asignarTag(alumno, nivelMedio)
        val grupo = crearGrupo("Maratón nivel medio", setOf(nivelMedio))
        enTransaccion { groups.assignCoach(club, grupo, entrenador) }

        val resultado = listar().single()

        resultado.groups.single().id shouldBe grupo
        resultado.groups.single().name shouldBe "Maratón nivel medio"
        resultado.groups.single().totalStudents shouldBe 1
        resultado.totalStudents shouldBe 1
    }

    @Test
    fun `un grupo asignado sin miembros aparece igualmente, con total cero`() {
        val entrenador = sembrarPersona("ENTRENADOR")
        val grupo = crearGrupo("Grupo vacío", emptySet())
        enTransaccion { groups.assignCoach(club, grupo, entrenador) }

        val resultado = listar().single()

        resultado.groups.single().totalStudents shouldBe 0
        resultado.totalStudents shouldBe 0
    }

    /**
     * Es la razón de ser de `COUNT(DISTINCT alumno)`: si `totalStudents` sumara los contadores de cada grupo, este
     * entrenador saldría con 2, no con 1 -- el mismo alumno contado dos veces por estar en ambos grupos.
     */
    @Test
    fun `un alumno en dos grupos del mismo entrenador cuenta una vez en totalStudents`() {
        val entrenador = sembrarPersona("ENTRENADOR")
        val alumno = sembrarPersona("ALUMNO")
        val nivelMedio = sembrarValor("nivel", "medio")
        val objetivoMaraton = sembrarValor("objetivo", "maraton")
        asignarTag(alumno, nivelMedio)
        asignarTag(alumno, objetivoMaraton)
        val grupoA = crearGrupo("Por nivel", setOf(nivelMedio))
        val grupoB = crearGrupo("Por objetivo", setOf(objetivoMaraton))
        enTransaccion { groups.assignCoach(club, grupoA, entrenador) }
        enTransaccion { groups.assignCoach(club, grupoB, entrenador) }

        val resultado = listar().single()

        resultado.groups.sumOf { it.totalStudents } shouldBe 2
        resultado.totalStudents shouldBe 1
    }

    @Test
    fun `dos entrenadores del mismo grupo ven el mismo recuento, cada uno con su propia fila de grupo`() {
        val carlos = sembrarPersona("ENTRENADOR", nombre = "Carlos Ruiz")
        val marta = sembrarPersona("ENTRENADOR", nombre = "Marta López")
        val alumno = sembrarPersona("ALUMNO")
        val nivelMedio = sembrarValor("nivel", "medio")
        asignarTag(alumno, nivelMedio)
        val grupo = crearGrupo("Compartido", setOf(nivelMedio))
        enTransaccion { groups.assignCoach(club, grupo, carlos) }
        enTransaccion { groups.assignCoach(club, grupo, marta) }

        val resultado = listar()

        resultado.single { it.id == carlos }.totalStudents shouldBe 1
        resultado.single { it.id == marta }.totalStudents shouldBe 1
    }

    @Test
    fun `ordena por nombre`() {
        val zoe = sembrarPersona("ENTRENADOR", nombre = "Zoe Martín")
        val ana = sembrarPersona("ENTRENADOR", nombre = "Ana Ruiz")

        listar().map { it.id } shouldBe listOf(ana, zoe)
    }

    /**
     * Anti-IDOR: se llama siempre con el club del principal autenticado (`club`, no `otroClub`) -- pasar `otroClub`
     * como parámetro haría que `AuthScopeEnforcementAspect` lance `AuthScopeViolationException` antes de llegar al
     * SQL, lo que verificaría el aspecto en vez de la guardia `club_id` de este repositorio.
     */
    @Test
    fun `no devuelve entrenadores de otro club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        sembrarPersona("ENTRENADOR", club = otroClub)
        val propio = sembrarPersona("ENTRENADOR")

        listar().map { it.id } shouldBe listOf(propio)
    }

    private fun listar(): List<CoachWorkload> = enTransaccion { directory.listByClub(club) }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    /** Siembra un grupo y sus tags requeridos vía el propio repositorio: el doble no es del listado, es del grupo. */
    private fun crearGrupo(
        nombre: String,
        requiredTagValueIds: Set<TagValueId>,
    ): GroupId {
        val group = Group.create(club, nombre, requiredTagValueIds).shouldBeRight()
        enTransaccion { groups.save(club, group) }
        return group.id
    }

    private fun asignarTag(
        alumno: PersonId,
        valueId: TagValueId,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            club.value,
            alumno.value,
            valueId.value,
        )
    }

    /** Cuelga un valor nuevo de un eje nuevo: los tests no dependen así de la taxonomía sembrada por migración. */
    private fun sembrarValor(
        eje: String,
        valor: String,
    ): TagValueId {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        val valueId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            club.value,
            // Sufijo corto por el límite de 40 caracteres de la columna, tomado del final del UUID: los v7
            // comparten el prefijo temporal y colisionarían entre tests del mismo segundo.
            "$eje-${keyId.toString().takeLast(SUFIJO_UNICO)}",
        )
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            valueId,
            keyId,
            club.value,
            valor,
        )
        return TagValueId.of(valueId)
    }

    private fun sembrarPersona(
        rol: String,
        club: ClubId = this.club,
        nombre: String? = null,
    ): PersonId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            nombre ?: "Persona $rol",
            "persona-$id@club.test",
            rol,
            UuidCreator.getTimeOrderedEpoch(),
        )
        return PersonId.of(id)
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
