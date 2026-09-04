package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.domain.person.PersonId
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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.properties.Delegates

/**
 * Los adaptadores de clasificación contra Postgres real: es lo único que puede verificar el reemplazo diferencial, el
 * filtro por club y la comprobación de que la persona es alumno, porque todo eso vive en SQL.
 */
class StudentTagPersistenceIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var studentTags: StudentTagRepository

    @Autowired private lateinit var studentLookup: StudentLookup

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    // Un club propio por test, nunca el de bootstrap. Estos tests crean ejes y valores, y el contenedor Postgres es
    // único para toda la JVM: sembrar en el club canónico le deja ejes de más a quien comprueba su taxonomía. JUnit
    // instancia la clase una vez por test, así que este id es distinto en cada uno y no hace falta limpiar entre ellos.
    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    // `lateinit` no admite value classes, y estos ids lo son; la delegación da el mismo fallo claro si se leen antes
    // de sembrarlos.
    private var alumno: PersonId by Delegates.notNull()
    private var nivelMedio: TagValueId by Delegates.notNull()
    private var objetivoMaraton: TagValueId by Delegates.notNull()

    @BeforeEach
    fun prepara() {
        alumno = sembrarPersona("ALUMNO")
        nivelMedio = sembrarValor("nivel", "medio")
        objetivoMaraton = sembrarValor("objetivo", "maratón valencia")
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `reemplazar guarda los valores pedidos`() {
        enTransaccion { studentTags.replace(club, alumno, setOf(nivelMedio, objetivoMaraton)) }

        asignados() shouldBe setOf(nivelMedio, objetivoMaraton)
    }

    @Test
    fun `reemplazar por una lista vacia borra todas las asignaciones`() {
        enTransaccion { studentTags.replace(club, alumno, setOf(nivelMedio)) }

        enTransaccion { studentTags.replace(club, alumno, emptySet()) }

        asignados().shouldBeEmpty()
    }

    /**
     * La prueba de que el reemplazo aplica la diferencia en vez de borrar e insertar todo: si reescribiera las filas,
     * la fecha de la asignación que no cambia se perdería, y es la única traza de cuándo se clasificó al alumno.
     */
    @Test
    fun `reemplazar conserva la fecha de las asignaciones que no cambian`() {
        enTransaccion { studentTags.replace(club, alumno, setOf(nivelMedio)) }
        val original = creadoEn(nivelMedio)
        jdbc.update(
            "UPDATE club_taxonomia.alumno_tag SET creado_en = ? WHERE tag_value_id = ?",
            Timestamp.from(Instant.now().minusSeconds(DIEZ_DIAS)),
            nivelMedio.value,
        )
        val envejecida = creadoEn(nivelMedio)

        enTransaccion { studentTags.replace(club, alumno, setOf(nivelMedio, objetivoMaraton)) }

        creadoEn(nivelMedio) shouldBe envejecida
        (envejecida.before(original)) shouldBe true
    }

    @Test
    fun `asignar dos veces el mismo valor no duplica ni falla`() {
        enTransaccion { studentTags.add(club, alumno, nivelMedio) }
        enTransaccion { studentTags.add(club, alumno, nivelMedio) }

        asignados() shouldBe setOf(nivelMedio)
    }

    @Test
    fun `quitar un valor que no estaba asignado no falla`() {
        enTransaccion { studentTags.remove(club, alumno, nivelMedio) }

        asignados().shouldBeEmpty()
    }

    @Test
    fun `las asignaciones de otro club ni se leen ni se borran`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val ajeno = sembrarPersona("ALUMNO", club = otroClub)
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            otroClub.value,
            ajeno.value,
            nivelMedio.value,
        )

        enTransaccion { studentTags.replace(club, alumno, emptySet()) }

        contarAsignaciones(ajeno) shouldBe 1
    }

    /** LAL-83: aviso de impacto de archivado. */
    @Test
    fun `countStudentsWithAnyValue cuenta alumnos distintos sin duplicar por valor`() {
        enTransaccion { studentTags.replace(club, alumno, setOf(nivelMedio, objetivoMaraton)) }

        enTransaccion { studentTags.countStudentsWithAnyValue(club, setOf(nivelMedio, objetivoMaraton)) } shouldBe 1
    }

    @Test
    fun `countStudentsWithAnyValue sin alumnos asignados devuelve cero`() {
        enTransaccion { studentTags.countStudentsWithAnyValue(club, setOf(nivelMedio)) } shouldBe 0
    }

    @Test
    fun `countStudentsWithAnyValue con conjunto vacio devuelve cero sin consultar`() {
        enTransaccion { studentTags.replace(club, alumno, setOf(nivelMedio)) }

        enTransaccion { studentTags.countStudentsWithAnyValue(club, emptySet()) } shouldBe 0
    }

    @Test
    fun `countStudentsWithAnyValue no cuenta asignaciones de otro club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val ajeno = sembrarPersona("ALUMNO", club = otroClub)
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            otroClub.value,
            ajeno.value,
            nivelMedio.value,
        )

        enTransaccion { studentTags.countStudentsWithAnyValue(club, setOf(nivelMedio)) } shouldBe 0
    }

    @Test
    fun `reconoce como alumno a la persona del club con ese rol`() {
        enTransaccion { studentLookup.isStudent(club, alumno) } shouldBe true
    }

    @Test
    fun `no reconoce como alumno a un entrenador`() {
        val entrenador = sembrarPersona("ENTRENADOR")

        enTransaccion { studentLookup.isStudent(club, entrenador) } shouldBe false
    }

    @Test
    fun `no reconoce a una persona de otro club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val ajeno = sembrarPersona("ALUMNO", club = otroClub)

        enTransaccion { studentLookup.isStudent(club, ajeno) } shouldBe false
    }

    @Test
    fun `no reconoce a quien no existe`() {
        enTransaccion { studentLookup.isStudent(club, PersonId.of(UuidCreator.getTimeOrderedEpoch())) } shouldBe false
    }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun asignados(): Set<TagValueId> = enTransaccion { studentTags.findAssignedValueIds(club, alumno) }

    private fun creadoEn(valueId: TagValueId): Timestamp =
        jdbc.queryForObject(
            "SELECT creado_en FROM club_taxonomia.alumno_tag WHERE alumno_id = ? AND tag_value_id = ?",
            Timestamp::class.java,
            alumno.value,
            valueId.value,
        )!!

    private fun contarAsignaciones(personId: PersonId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.alumno_tag WHERE alumno_id = ?",
            Int::class.java,
            personId.value,
        ) ?: 0

    private fun sembrarPersona(
        rol: String,
        club: ClubId = this.club,
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
            "Persona $rol",
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
    ): TagValueId {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        val valueId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            club.value,
            // Sufijo corto por el límite de 40 caracteres de la columna, y tomado del **final** del UUID: los v7
            // comparten el prefijo temporal, así que los primeros caracteres colisionan entre tests del mismo
            // segundo y el índice único del nombre los rechazaría.
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
        const val DIEZ_DIAS = 864_000L
        const val SUFIJO_UNICO = 8
    }
}
