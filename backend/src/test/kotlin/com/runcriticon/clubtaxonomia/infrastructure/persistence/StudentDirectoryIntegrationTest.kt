package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentDirectory
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
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
import kotlin.properties.Delegates

/**
 * El listado de alumnos con filtro por tags (AND, ADR-0002 D3) contra Postgres real: es lo único que puede verificar el
 * `HAVING COUNT(DISTINCT ...)` y el filtro por club, porque vive en SQL, no en el dominio.
 */
class StudentDirectoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var directory: StudentDirectory

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    // Club propio por test, nunca el de bootstrap: el contenedor Postgres es único para toda la JVM.
    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    private var nivelMedio: TagValueId by Delegates.notNull()
    private var objetivoMaraton: TagValueId by Delegates.notNull()

    @BeforeEach
    fun prepara() {
        nivelMedio = sembrarValor("nivel", "medio")
        objetivoMaraton = sembrarValor("objetivo", "maraton")
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `sin filtro devuelve todos los alumnos del club`() {
        val ana = sembrarPersona("ALUMNO", nombre = "Ana Ruiz")
        val zoe = sembrarPersona("ALUMNO", nombre = "Zoe Martín")

        listar(emptySet()).map { it.id } shouldBe listOf(ana, zoe)
    }

    /** El estado base del listado es la lista completa: es lo contrario de la previsualización de un grupo. */
    @Test
    fun `sin filtro y sin ningun alumno la lista sale vacia, no un error`() {
        listar(emptySet()).shouldBeEmpty()
    }

    @Test
    fun `sin filtro un alumno sin ningun tag sale con valores vacio`() {
        sembrarPersona("ALUMNO")

        listar(emptySet()).single().tagValueIds.shouldBeEmpty()
    }

    @Test
    fun `sin filtro excluye a los entrenadores`() {
        sembrarPersona("ENTRENADOR")
        val alumno = sembrarPersona("ALUMNO")

        listar(emptySet()).map { it.id } shouldBe listOf(alumno)
    }

    @Test
    fun `con un tag solo devuelve a quien lo tiene`() {
        val conTag = sembrarPersona("ALUMNO", nombre = "Ana Ruiz")
        asignarTag(conTag, nivelMedio)
        sembrarPersona("ALUMNO", nombre = "Zoe Martín")

        listar(setOf(nivelMedio)).map { it.id } shouldBe listOf(conTag)
    }

    @Test
    fun `con dos tags exige ambos, quien solo tiene uno queda fuera`() {
        val ambos = sembrarPersona("ALUMNO")
        asignarTag(ambos, nivelMedio)
        asignarTag(ambos, objetivoMaraton)
        val soloUno = sembrarPersona("ALUMNO")
        asignarTag(soloUno, nivelMedio)

        listar(setOf(nivelMedio, objetivoMaraton)).map { it.id } shouldBe listOf(ambos)
    }

    /** Filtro de lectura, no validación de escritura: un id que no matchea a nadie no rompe la petición. */
    @Test
    fun `un tagValueId inexistente da lista vacia sin error`() {
        sembrarPersona("ALUMNO")

        listar(setOf(TagValueId.of(UuidCreator.getTimeOrderedEpoch()))).shouldBeEmpty()
    }

    @Test
    fun `un tagValueId de otro club da lista vacia`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val valorDeOtroClub = sembrarValor("nivel", "medio", club = otroClub)
        sembrarPersona("ALUMNO")

        listar(setOf(valorDeOtroClub)).shouldBeEmpty()
    }

    /** Archivar un valor no borra las asignaciones existentes: el filtro sigue funcionando sobre lo ya asignado. */
    @Test
    fun `un valor archivado sigue filtrando si el alumno ya lo tenia asignado`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        archivarValor(nivelMedio)

        listar(setOf(nivelMedio)).map { it.id } shouldBe listOf(alumno)
    }

    @Test
    fun `valores trae todos los tags del alumno, no solo los del filtro`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        asignarTag(alumno, objetivoMaraton)

        listar(setOf(nivelMedio)).single().tagValueIds shouldBe setOf(nivelMedio, objetivoMaraton)
    }

    @Test
    fun `ordena por nombre`() {
        val zoe = sembrarPersona("ALUMNO", nombre = "Zoe Martín")
        val ana = sembrarPersona("ALUMNO", nombre = "Ana Ruiz")

        listar(emptySet()).map { it.id } shouldBe listOf(ana, zoe)
    }

    /**
     * Anti-IDOR: se llama siempre con el club del principal autenticado (`club`, no `otroClub`) -- pasar `otroClub`
     * como parámetro haría que `AuthScopeEnforcementAspect` lance `AuthScopeViolationException` antes de llegar al
     * SQL, lo que verificaría el aspecto en vez de la guardia `club_id` de este repositorio.
     */
    @Test
    fun `no devuelve alumnos de otro club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        sembrarPersona("ALUMNO", club = otroClub)
        val propio = sembrarPersona("ALUMNO")

        listar(emptySet()).map { it.id } shouldBe listOf(propio)
    }

    private fun listar(tags: Set<TagValueId>): List<StudentSummary> = enTransaccion { directory.listByClub(club, tags) }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

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

    private fun archivarValor(valueId: TagValueId) {
        jdbc.update(
            "UPDATE club_taxonomia.tag_value SET archivado_en = now() WHERE id = ?",
            valueId.value,
        )
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

    /** Cuelga un valor nuevo de un eje nuevo: los tests no dependen así de la taxonomía sembrada por migración. */
    private fun sembrarValor(
        eje: String,
        valor: String,
        club: ClubId = this.club,
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
