package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
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
 * La resolución de membresía (ADR-0002 D3+D4) contra Postgres real: es lo único que puede verificar el `HAVING
 * COUNT(DISTINCT ...)`, la prevalencia de `EXCEPT` sobre los overrides y el filtro por club, porque todo eso vive
 * en el SQL canónico, no en el dominio.
 *
 * Dos casos del catálogo de test de ADR-0002 (líneas 395-406) no se escriben aquí porque son inalcanzables con
 * datos legítimos bajo las PK actuales:
 *  - "tags repetidos en alumno_tag": su PK es `(alumno_id, tag_value_id)`, no admite duplicados reales. El
 *    `COUNT(DISTINCT ...)` del SQL es defensa ante un escenario que la BD ya impide.
 *  - "ambos overrides a la vez" (incluido y excluido): la PK de `grupo_alumno_override` es `(grupo_id, alumno_id)`
 *    -- un alumno tiene un único estado por grupo. Verificado contra `docs/diseno/constructor-grupos.html`
 *    ("Ajustes manuales": dos listas disjuntas, "Quitar inclusión"/"Restaurar al grupo").
 */
class GroupRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var groups: GroupRepository

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
    fun `save persiste el grupo y sus tags requeridos`() {
        val group = Group.create(club, "Maraton Valencia avanzado", setOf(nivelMedio, objetivoMaraton)).shouldBeRight()

        enTransaccion { groups.save(club, group) }

        nombreGuardado(group.id) shouldBe "Maraton Valencia avanzado"
        tagsRequeridosGuardados(group.id) shouldBe setOf(nivelMedio, objetivoMaraton)
    }

    @Test
    fun `alumno con todos los tags requeridos esta en el grupo`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        asignarTag(alumno, objetivoMaraton)
        val grupo = crearGrupo("Con todos los tags", setOf(nivelMedio, objetivoMaraton))

        resolver(grupo) shouldBe setOf(alumno)
    }

    @Test
    fun `alumno con solo algunos de los tags requeridos no esta en el grupo`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        val grupo = crearGrupo("Con todos los tags", setOf(nivelMedio, objetivoMaraton))

        resolver(grupo).shouldBeEmpty()
    }

    @Test
    fun `grupo sin tags requeridos y sin overrides no tiene miembros`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        val grupo = crearGrupo("Solo incluidos manualmente", emptySet())

        resolver(grupo).shouldBeEmpty()
    }

    @Test
    fun `un override incluido mete a un alumno que no cumple los tags`() {
        val alumno = sembrarPersona("ALUMNO")
        val grupo = crearGrupo("Con excepcion", setOf(nivelMedio))
        insertarOverride(grupo, alumno, incluido = true)

        resolver(grupo) shouldBe setOf(alumno)
    }

    @Test
    fun `un override excluido saca a un alumno que si cumple los tags`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        val grupo = crearGrupo("Con excepcion", setOf(nivelMedio))
        insertarOverride(grupo, alumno, incluido = false)

        resolver(grupo).shouldBeEmpty()
    }

    @Test
    fun `cambiar los tags de un alumno cambia el resultado en la siguiente resolucion`() {
        val alumno = sembrarPersona("ALUMNO")
        val grupo = crearGrupo("Con todos los tags", setOf(nivelMedio))
        resolver(grupo).shouldBeEmpty()

        asignarTag(alumno, nivelMedio)

        resolver(grupo) shouldBe setOf(alumno)
    }

    @Test
    fun `un alumno borrado desaparece de la resolucion`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        val grupo = crearGrupo("Con todos los tags", setOf(nivelMedio))
        resolver(grupo) shouldBe setOf(alumno)

        // Efecto ya cubierto en producción por StudentDeletionListener (LAL-77): aquí se reproduce directamente.
        jdbc.update("DELETE FROM club_taxonomia.alumno_tag WHERE alumno_id = ?", alumno.value)

        resolver(grupo).shouldBeEmpty()
    }

    /**
     * Anti-IDOR: un grupo de otro club no resuelve miembros aunque el club llamador sea uno legítimo. La llamada
     * usa siempre el club del propio principal autenticado (`club`, no `otroClub`) -- pasar `otroClub` como
     * parámetro haría que `AuthScopeEnforcementAspect` lance `AuthScopeViolationException` antes de llegar al SQL,
     * lo que verificaría el aspecto en vez de la guardia `club_id` de este repositorio.
     */
    @Test
    fun `un grupo de otro club no resuelve miembros`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val valorDeOtroClub = sembrarValor("nivel", "medio", club = otroClub)
        val alumnoDeOtroClub = sembrarPersona("ALUMNO", club = otroClub)
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            otroClub.value,
            alumnoDeOtroClub.value,
            valorDeOtroClub.value,
        )
        val grupoDeOtroClub = crearGrupo("Grupo ajeno", setOf(valorDeOtroClub), club = otroClub)

        enTransaccion { groups.resolveMembers(club, grupoDeOtroClub) }.shouldBeEmpty()
    }

    @Test
    fun `previsualizar devuelve al alumno que cumple todo el filtro, con su nombre`() {
        val alumno = sembrarPersona("ALUMNO", nombre = "Pedro Cordero")
        asignarTag(alumno, nivelMedio)
        asignarTag(alumno, objetivoMaraton)

        val miembros = previsualizar(setOf(nivelMedio, objetivoMaraton))

        miembros.total shouldBe 1
        miembros.members.single().id shouldBe alumno
        miembros.members.single().name shouldBe "Pedro Cordero"
    }

    @Test
    fun `previsualizar excluye al alumno que solo cumple parte del filtro`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)

        previsualizar(setOf(nivelMedio, objetivoMaraton)).members.shouldBeEmpty()
    }

    /** Misma semántica que un grupo guardado sin tags requeridos: sin filtro no entra nadie, no entra todo el club. */
    @Test
    fun `previsualizar sin filtro no devuelve a nadie`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)

        previsualizar(emptySet()).total shouldBe 0
    }

    @Test
    fun `previsualizar ignora a los entrenadores aunque lleven los tags`() {
        val entrenador = sembrarPersona("ENTRENADOR")
        asignarTag(entrenador, nivelMedio)

        previsualizar(setOf(nivelMedio)).members.shouldBeEmpty()
    }

    /** El JOIN es INNER: una asignación huérfana es una inconsistencia, no un miembro que devolver sin nombre. */
    @Test
    fun `previsualizar ignora una asignacion sin persona en la proyeccion`() {
        val huerfano = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        asignarTag(huerfano, nivelMedio)

        previsualizar(setOf(nivelMedio)).members.shouldBeEmpty()
    }

    /** Las excepciones manuales cuelgan de un grupo; una previsualización no tiene grupo del que colgarlas. */
    @Test
    fun `previsualizar no se ve afectada por los overrides de ningun grupo`() {
        val conTags = sembrarPersona("ALUMNO")
        asignarTag(conTags, nivelMedio)
        val sinTags = sembrarPersona("ALUMNO")
        val grupo = crearGrupo("Con ajustes manuales", setOf(nivelMedio))
        insertarOverride(grupo, sinTags, incluido = true)
        insertarOverride(grupo, conTags, incluido = false)

        previsualizar(setOf(nivelMedio)).members.map { it.id } shouldBe listOf(conTags)
    }

    /**
     * Anti-IDOR, mismo criterio que el test del grupo ajeno: se llama siempre con el club del principal autenticado,
     * porque pasar `otroClub` haría saltar el aspecto antes del SQL y se estaría verificando el aspecto.
     */
    @Test
    fun `previsualizar no cruza la frontera de club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val valorDeOtroClub = sembrarValor("nivel", "medio", club = otroClub)
        val alumnoDeOtroClub = sembrarPersona("ALUMNO", club = otroClub)
        jdbc.update(
            "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?)",
            otroClub.value,
            alumnoDeOtroClub.value,
            valorDeOtroClub.value,
        )

        previsualizar(setOf(valorDeOtroClub)).members.shouldBeEmpty()
    }

    @Test
    fun `previsualizar ordena por nombre y el total cuadra con la lista`() {
        val ana = sembrarPersona("ALUMNO", nombre = "Ana Ruiz")
        val zoe = sembrarPersona("ALUMNO", nombre = "Zoe Martín")
        listOf(zoe, ana).forEach { asignarTag(it, nivelMedio) }

        val miembros = previsualizar(setOf(nivelMedio))

        miembros.members.map { it.name } shouldBe listOf("Ana Ruiz", "Zoe Martín")
        miembros.total shouldBe miembros.members.size
    }

    @Test
    fun `el listado cuenta a los alumnos que cumplen todo el filtro`() {
        val cumple = sembrarPersona("ALUMNO")
        asignarTag(cumple, nivelMedio)
        asignarTag(cumple, objetivoMaraton)
        val aMedias = sembrarPersona("ALUMNO")
        asignarTag(aMedias, nivelMedio)
        val grupo = crearGrupo("Con todos los tags", setOf(nivelMedio, objetivoMaraton))

        val resumen = listar().single { it.group.id == grupo }

        resumen.memberCount shouldBe 1
        resumen.group.requiredTagValueIds shouldBe setOf(nivelMedio, objetivoMaraton)
    }

    @Test
    fun `el listado suma a los incluidos manualmente y resta a los excluidos`() {
        val cumple = sembrarPersona("ALUMNO")
        asignarTag(cumple, nivelMedio)
        val ajeno = sembrarPersona("ALUMNO")
        val grupo = crearGrupo("Con excepciones", setOf(nivelMedio))
        insertarOverride(grupo, ajeno, incluido = true)

        listar().single { it.group.id == grupo }.memberCount shouldBe 2

        insertarOverride(grupo, cumple, incluido = false)

        listar().single { it.group.id == grupo }.memberCount shouldBe 1
    }

    /**
     * Aquí es donde el recuento del listado diverge a propósito de la resolución de un grupo guardado: esta se apoya en
     * la proyección de personas, así que una asignación huérfana no cuenta y un entrenador con los mismos tags tampoco.
     */
    @Test
    fun `el listado no cuenta asignaciones sin persona ni a los entrenadores`() {
        val huerfano = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        asignarTag(huerfano, nivelMedio)
        val entrenador = sembrarPersona("ENTRENADOR")
        asignarTag(entrenador, nivelMedio)
        val grupo = crearGrupo("Solo alumnos", setOf(nivelMedio))

        listar().single { it.group.id == grupo }.memberCount shouldBe 0
        resolver(grupo) shouldBe setOf(huerfano, entrenador)
    }

    @Test
    fun `un grupo sin miembros aparece en el listado con cero`() {
        val grupo = crearGrupo("Nadie encaja", setOf(nivelMedio))

        listar().single { it.group.id == grupo }.memberCount shouldBe 0
    }

    @Test
    fun `un grupo sin tags requeridos solo cuenta a los incluidos manualmente`() {
        val alumno = sembrarPersona("ALUMNO")
        asignarTag(alumno, nivelMedio)
        val aMano = sembrarPersona("ALUMNO")
        val grupo = crearGrupo("Solo a mano", emptySet())
        insertarOverride(grupo, aMano, incluido = true)

        listar().single { it.group.id == grupo }.memberCount shouldBe 1
    }

    @Test
    fun `el listado no devuelve los grupos de otro club`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val grupoDeOtroClub = crearGrupo("Grupo ajeno", emptySet(), club = otroClub)
        val propio = crearGrupo("Grupo propio", emptySet())

        val ids = listar().map { it.group.id }

        ids shouldContain propio
        ids shouldNotContain grupoDeOtroClub
    }

    @Test
    fun `el listado ordena por nombre y devuelve el filtro de forma estable`() {
        crearGrupo("Zoco", setOf(nivelMedio, objetivoMaraton))
        crearGrupo("Alfa", setOf(objetivoMaraton))

        val primera = listar()
        val segunda = listar()

        primera.map { it.group.name.value } shouldBe listOf("Alfa", "Zoco")
        primera.map { it.group.requiredTagValueIds.toList() } shouldBe
            segunda.map { it.group.requiredTagValueIds.toList() }
    }

    private fun resolver(groupId: GroupId): Set<PersonId> = enTransaccion { groups.resolveMembers(club, groupId) }

    private fun listar(): List<GroupSummary> = enTransaccion { groups.listSummaries(club) }

    private fun previsualizar(tags: Set<TagValueId>): GroupMembers = enTransaccion { groups.previewMembers(club, tags) }

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun nombreGuardado(groupId: GroupId): String =
        jdbc.queryForObject(
            "SELECT nombre FROM club_taxonomia.grupo WHERE id = ?",
            String::class.java,
            groupId.value,
        )!!

    private fun tagsRequeridosGuardados(groupId: GroupId): Set<TagValueId> =
        jdbc
            .queryForList(
                "SELECT tag_value_id FROM club_taxonomia.grupo_tag_requerido WHERE grupo_id = ?",
                UUID::class.java,
                groupId.value,
            ).filterNotNull()
            .mapTo(mutableSetOf()) { TagValueId.of(it) }

    /** Siembra un grupo y sus tags requeridos con SQL crudo: el repositorio bajo prueba es `resolveMembers`. */
    private fun crearGrupo(
        nombre: String,
        requiredTagValueIds: Set<TagValueId>,
        club: ClubId = this.club,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)",
            id,
            club.value,
            nombre,
        )
        requiredTagValueIds.forEach { valueId ->
            jdbc.update(
                "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)",
                id,
                club.value,
                valueId.value,
            )
        }
        return GroupId.of(id)
    }

    private fun insertarOverride(
        groupId: GroupId,
        alumno: PersonId,
        incluido: Boolean,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.grupo_alumno_override (grupo_id, club_id, alumno_id, incluido) " +
                "VALUES (?, ?, ?, ?)",
            groupId.value,
            club.value,
            alumno.value,
            incluido,
        )
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
