package com.runcriticon.clubtaxonomia.performance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories.LIST_SUMMARIES_CLUB_PARAMS
import com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories.LIST_SUMMARIES_SQL
import com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories.RESOLVE_MEMBERS_SQL
import com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories.resolveMembersArgs
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.sql.PreparedStatement
import java.util.UUID
import kotlin.math.ceil
import kotlin.properties.Delegates

private const val ALUMNO_COUNT = 5000
private const val TAG_KEY_COUNT = 30
private const val TAG_VALUES_PER_KEY = 100
private const val GROUP_COUNT = 200
private const val SLOTS_PER_ALUMNO = 50
private const val EXPECTED_ALUMNO_TAG_ROWS = ALUMNO_COUNT * SLOTS_PER_ALUMNO
private const val EXPECTED_MEASURED_GROUP_MEMBERS = 200 // 5.000 / 25: interseccion de dos ejes al 20% cada uno
private const val WARMUP_ITERATIONS = 10
private const val MEASURED_ITERATIONS = 100
private const val P95_FRACTION = 0.95
private const val P95_THRESHOLD_MILLIS = 400L
private const val NANOS_PER_MILLI = 1_000_000L
private const val SEQ_SCAN_NODE_TYPE = "Seq Scan"
private const val MEASURED_TABLE = "alumno_tag"
private const val SUFIJO_UNICO = 8

/**
 * RNF de dimensionado (LAL-95, ADR-0002 RNF): con un club a escala (5.000 alumnos, 30 ejes de tag x 100 valores,
 * 200 grupos, 250.000 filas en `alumno_tag`), la resolucion de membresia ([RESOLVE_MEMBERS_SQL]) y el listado de
 * grupos ([LIST_SUMMARIES_SQL]) cumplen p95 < 400 ms (ADR-0001) y usan los indices de ADR-0002 D3, sin barrido
 * secuencial sobre `alumno_tag`. Si el umbral no se cumple, el hallazgo se documenta -- no se mete cache
 * (aplazada, ADR-0015): "medir primero".
 *
 * **Selectividad, no solo volumen.** Con asignacion uniforme sobre 3.000 valores, un grupo con 2-3 tags exigidos
 * casaria con ~0 alumnos: la consulta devolveria vacio en microsegundos y el p95 pasaria sin medir nada. En el
 * extremo contrario (un valor que tiene el 40% del club), el barrido secuencial pasa a ser el plan *correcto* y
 * el criterio de "sin seq scan" fallaria legitimamente. El dataset fija dos ejes "calientes", 20% de selectividad
 * cada uno e independientes entre si (`alumno i`: eje0 = `i % 5`, eje1 = `(i / 5) % 5`), de forma que el grupo
 * medido -- que exige un valor de cada uno -- casa con exactamente 200 alumnos (5.000 / 25): ni vacio ni la mitad
 * del club. Los 28 ejes restantes reparten el resto de las 250.000 filas con baja selectividad (~1-2%).
 * Todo por aritmetica modular, nunca `random()`: reproducible entre ejecuciones.
 *
 * `ANALYZE` tras sembrar es obligatorio (ver [analizarTablas]): recien insertadas las 250.000 filas el
 * planificador no tiene estadisticas y elegiria un plan arbitrario -- el criterio de "sin seq scan" fallaria por
 * un motivo ajeno a los indices de ADR-0002 D3.
 *
 * `@TestInstance(PER_CLASS)` + `@BeforeAll` de instancia: sembrar 250.000 filas es el coste caro de este test:
 * hacerlo una vez para las dos consultas medidas (no una vez por `@Test`) es la diferencia entre segundos y
 * minutos. Filtrado del build normal por FQN de paquete (`backend/build.gradle.kts`, tarea `loadTest`): los
 * specs Kotest de este proyecto no propagan `@Tag` al `TagFilter` de JUnit Platform, verificado empiricamente.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupResolutionLoadTest : IntegrationTestBase() {
    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    private val objectMapper = ObjectMapper()

    // Club propio de esta clase, nunca el de bootstrap: el contenedor Postgres es unico para toda la JVM.
    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    // GroupId es un value class (inline class): `lateinit` no está permitido sobre esos tipos, de ahí el
    // delegado, mismo patrón que TagValueId en GroupRepositoryIntegrationTest.
    private var measuredGroup: GroupId by Delegates.notNull()

    @BeforeAll
    fun seedDataset() {
        val axisKeyIds = (0 until TAG_KEY_COUNT).map { sembrarEje(it) }
        axisKeyIds.forEach { sembrarValoresDeEje(it) }
        val alumnoIds = sembrarAlumnos()
        sembrarAsignacionesDeTags(alumnoIds, axisKeyIds)
        measuredGroup = sembrarGrupoMedido(axisKeyIds)
        sembrarGruposDeRelleno(axisKeyIds)
        analizarTablas()
    }

    @BeforeEach
    fun autenticar() {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                admin,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${admin.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `resolveMembers cumple el p95 a escala y usa los indices de ADR-0002 D3`() {
        repeat(WARMUP_ITERATIONS) { enTransaccion { groups.resolveMembers(club, measuredGroup) } }

        val nanos =
            (1..MEASURED_ITERATIONS)
                .map { measureNanos { enTransaccion { groups.resolveMembers(club, measuredGroup) } } }
                .sorted()
        val p95Millis = percentileMillis(nanos, P95_FRACTION)

        enTransaccion { groups.resolveMembers(club, measuredGroup) } shouldHaveSize EXPECTED_MEASURED_GROUP_MEMBERS

        val resumen =
            "p95 resolveMembers = $p95Millis ms sobre $MEASURED_ITERATIONS muestras " +
                "($ALUMNO_COUNT alumnos, $EXPECTED_ALUMNO_TAG_ROWS filas alumno_tag)"
        println(resumen) // visibilidad al ejecutar `./gradlew loadTest` a mano, no solo en el fallo del assert
        withClue(resumen) {
            p95Millis shouldBeLessThan P95_THRESHOLD_MILLIS
        }

        explainPlanFor(RESOLVE_MEMBERS_SQL, resolveMembersArgs(measuredGroup, club)) shouldHaveNoSeqScanOn
            MEASURED_TABLE
    }

    @Test
    fun `listSummaries cumple el p95 a escala y usa los indices de ADR-0002 D3`() {
        repeat(WARMUP_ITERATIONS) { enTransaccion { groups.listSummaries(club) } }

        val nanos =
            (1..MEASURED_ITERATIONS)
                .map { measureNanos { enTransaccion { groups.listSummaries(club) } } }
                .sorted()
        val p95Millis = percentileMillis(nanos, P95_FRACTION)

        enTransaccion { groups.listSummaries(club) }
            .single { it.group.id == measuredGroup }
            .memberCount shouldBe EXPECTED_MEASURED_GROUP_MEMBERS

        val resumen = "p95 listSummaries = $p95Millis ms sobre $MEASURED_ITERATIONS muestras ($GROUP_COUNT grupos)"
        println(resumen)
        withClue(resumen) {
            p95Millis shouldBeLessThan P95_THRESHOLD_MILLIS
        }

        val args = Array<Any>(LIST_SUMMARIES_CLUB_PARAMS) { club.value }
        explainPlanFor(LIST_SUMMARIES_SQL, args) shouldHaveNoSeqScanOn MEASURED_TABLE
    }

    // --- siembra ---

    private fun sembrarEje(axis: Int): UUID {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            club.value,
            // Sufijo por el limite de 40 caracteres y para no colisionar entre ejes del mismo segundo (UUID v7).
            "perf$axis-${keyId.toString().takeLast(SUFIJO_UNICO)}",
        )
        return keyId
    }

    private fun sembrarValoresDeEje(keyId: UUID) {
        val filas =
            (0 until TAG_VALUES_PER_KEY).map { valueIndex ->
                arrayOf<Any>(UuidCreator.getTimeOrderedEpoch(), keyId, club.value, "v$valueIndex")
            }
        jdbc.batchUpdate(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            filas,
        )
    }

    private fun sembrarAlumnos(): List<UUID> {
        val ids = (0 until ALUMNO_COUNT).map { UuidCreator.getTimeOrderedEpoch() }
        val filas =
            ids.mapIndexed { i, id ->
                arrayOf<Any>(
                    id,
                    club.value,
                    "Alumno perf $i",
                    "alumno-perf-$i@club.test",
                    "ALUMNO",
                    "ACTIVO",
                    UuidCreator.getTimeOrderedEpoch(),
                )
            }
        jdbc.batchUpdate(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            """.trimIndent(),
            filas,
        )
        return ids
    }

    /**
     * Las 250.000 filas en una sola sentencia (`generate_series` de alumno x slot), no fila a fila. El `LATERAL`
     * calcula, por slot `t.j` (0..49), a que eje y que indice de valor corresponde:
     *  - `t.j = 0`: eje 0 (caliente), valor = `i % 5`.
     *  - `t.j = 1`: eje 1 (caliente), valor = `(i / 5) % 5`.
     *  - `t.j` >= 2: 28 ejes templados (`axisKeyIds[2..29]`); 20 de ellos reciben dos slots (el segundo desplazado
     *    `+50 mod 100` para que nunca coincida con el primero -- sin el desplazamiento, para los alumnos con
     *    `i mod 25 == 24` ambos slots calculan el mismo indice y el `INSERT` viola la PK `(alumno_id,
     *    tag_value_id)`) y los 8 restantes reciben uno solo. 20*2 + 8*1 = 48 slots templados + 2 calientes = 50.
     *
     * Solo dos arrays van como parametro (alumnos y los 30 ids de eje): los 3.000 ids de valor se resuelven por
     * `JOIN` contra `tag_value` por `(tag_key_id, nombre)`, no hace falta pasarlos.
     */
    private fun sembrarAsignacionesDeTags(
        alumnoIds: List<UUID>,
        axisKeyIds: List<UUID>,
    ) {
        jdbc.update(ALUMNO_TAG_SEED_SQL) { statement: PreparedStatement ->
            statement.setObject(1, club.value)
            statement.setArray(2, statement.connection.createArrayOf("uuid", alumnoIds.toTypedArray()))
            statement.setArray(3, statement.connection.createArrayOf("uuid", axisKeyIds.toTypedArray()))
        }
    }

    private fun sembrarGrupoMedido(axisKeyIds: List<UUID>): GroupId {
        val axis0Value = valorDeEje(axisKeyIds[0], 0)
        val axis1Value = valorDeEje(axisKeyIds[1], 0)
        return crearGrupo("Grupo medido (perf)", setOf(axis0Value, axis1Value))
    }

    /** 199 grupos mas (200 en total con el medido), con un filtro de baja selectividad cada uno. */
    private fun sembrarGruposDeRelleno(axisKeyIds: List<UUID>) {
        val ejeDeRelleno = axisKeyIds[2]
        (1 until GROUP_COUNT).forEach { k ->
            crearGrupo("Grupo relleno $k", setOf(valorDeEje(ejeDeRelleno, k % TAG_VALUES_PER_KEY)))
        }
    }

    private fun valorDeEje(
        keyId: UUID,
        valueIndex: Int,
    ): UUID =
        jdbc.queryForObject(
            "SELECT id FROM club_taxonomia.tag_value WHERE tag_key_id = ? AND nombre = ?",
            UUID::class.java,
            keyId,
            "v$valueIndex",
        )!!

    private fun crearGrupo(
        nombre: String,
        requiredTagValueIds: Set<UUID>,
    ): GroupId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        requiredTagValueIds.forEach { valueId ->
            jdbc.update(
                "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)",
                id,
                club.value,
                valueId,
            )
        }
        return GroupId.of(id)
    }

    /** Sin esto el planificador no tiene estadisticas sobre las 250.000 filas recien insertadas y elige un plan
     * arbitrario -- el criterio de "sin seq scan" fallaria por eso, no por los indices de ADR-0002 D3. */
    private fun analizarTablas() {
        listOf("alumno_tag", "grupo_tag_requerido", "persona", "grupo_alumno_override").forEach {
            jdbc.execute("ANALYZE club_taxonomia.$it")
        }
    }

    // --- medicion ---

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun <T> measureNanos(block: () -> T): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    /** p95 sobre una lista ya ordenada ascendente, en milisegundos. */
    private fun percentileMillis(
        sortedNanos: List<Long>,
        fraction: Double,
    ): Long {
        val index = (ceil(fraction * sortedNanos.size).toInt() - 1).coerceIn(0, sortedNanos.size - 1)
        return sortedNanos[index] / NANOS_PER_MILLI
    }

    /**
     * `EXPLAIN (ANALYZE, FORMAT JSON)` -- no `EXPLAIN` a secas, que con parametros posicionales puede reportar un
     * plan generico distinto del que realmente se ejecuta.
     */
    private fun explainPlanFor(
        sql: String,
        args: Array<Any>,
    ): JsonNode {
        val json =
            enTransaccion {
                jdbc.queryForObject("EXPLAIN (ANALYZE, FORMAT JSON) $sql", String::class.java, *args)
            }!!
        return objectMapper.readTree(json)[0]["Plan"]
    }

    private infix fun JsonNode.shouldHaveNoSeqScanOn(relation: String) {
        withClue(this.toPrettyString()) {
            hasSeqScanOn(relation) shouldBe false
        }
    }

    private fun JsonNode.hasSeqScanOn(relation: String): Boolean {
        val matchesHere =
            this["Node Type"]?.asText() == SEQ_SCAN_NODE_TYPE && this["Relation Name"]?.asText() == relation
        if (matchesHere) return true
        return this["Plans"]?.any { it.hasSeqScanOn(relation) } ?: false
    }
}

/** Ver el KDoc de [GroupResolutionLoadTest.sembrarAsignacionesDeTags]. 3 parametros: club, alumnos[], ejes[]. */
private const val ALUMNO_TAG_SEED_SQL =
    """
    INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id)
    SELECT
        ? AS club_id,
        (?)[a.i + 1] AS alumno_id,
        tv.id AS tag_value_id
    FROM generate_series(0, 4999) AS a(i)
    CROSS JOIN generate_series(0, 49) AS t(j)
    CROSS JOIN LATERAL (
        SELECT
            CASE
                WHEN t.j = 0 THEN 0
                WHEN t.j = 1 THEN 1
                WHEN (t.j - 2) < 20 THEN 2 + (t.j - 2)
                ELSE 2 + (t.j - 2 - 20)
            END AS axis_idx,
            CASE
                WHEN t.j = 0 THEN a.i % 5
                WHEN t.j = 1 THEN (a.i / 5) % 5
                WHEN (t.j - 2) < 20 THEN (a.i * (t.j - 2 + 1) + (t.j - 2)) % 100
                WHEN (t.j - 2) < 40 THEN (a.i * (t.j - 2 - 20 + 1) + (t.j - 2 - 20) + 50) % 100
                ELSE (a.i * (t.j - 2 - 20 + 1) + (t.j - 2 - 20)) % 100
            END AS value_index
    ) AS slot
    JOIN club_taxonomia.tag_value tv
      ON tv.tag_key_id = (?)[slot.axis_idx + 1]
     AND tv.nombre = 'v' || slot.value_index
    """
