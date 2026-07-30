package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * El club de bootstrap arranca con la taxonomía por defecto ya sembrada por su migración: un club que abre el editor
 * ante una lista vacía es el riesgo que esa siembra existe para evitar.
 *
 * Se lee por el repositorio y no con SQL directo a propósito: el orden de presentación no lo decide la base de datos
 * —los repositorios no ordenan— sino el mapper del adaptador, así que solo pasando por él se comprueba lo que va a
 * ver el editor.
 */
class TaxonomySeedIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var taxonomyRepository: TaxonomyRepository

    // El club canónico de `runcriticon.bootstrap.club-id`, que es el que siembra la migración.
    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)

    @BeforeEach
    fun autenticar() {
        // findByClub lleva @AuthScope(CLUB) y el aspecto contrasta el clubId de la firma con el del principal.
        val authentication =
            UsernamePasswordAuthenticationToken(
                admin,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${admin.role.name}")),
            )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `el club de bootstrap arranca con los cinco ejes por defecto en orden`() {
        val taxonomy = leerTaxonomia()

        taxonomy.activeKeys().map { it.label.value } shouldBe
            listOf("nivel", "distancia", "objetivo", "terreno", "estado")
    }

    @Test
    fun `cada eje trae sus valores por defecto en orden`() {
        val taxonomy = leerTaxonomia()

        valoresDe(taxonomy, "nivel") shouldBe listOf("iniciación", "medio", "medio-alto", "alto")
        valoresDe(taxonomy, "distancia") shouldBe listOf("1500m", "5k", "10k", "media maratón", "maratón")
        // El eje `objetivo` nace solo con el valor neutro: las carreras llegan con su propio catálogo.
        valoresDe(taxonomy, "objetivo") shouldBe listOf("sin carrera")
        valoresDe(taxonomy, "terreno") shouldBe listOf("asfalto", "trail", "pista")
        valoresDe(taxonomy, "estado") shouldBe listOf("activo", "lesión", "post-parto", "descanso")
    }

    @Test
    fun `ningun valor sembrado lleva metadata de carrera`() {
        val tipos =
            jdbc.queryForList(
                "SELECT DISTINCT metadata ->> 'tipo' FROM club_taxonomia.tag_value WHERE club_id = ?",
                String::class.java,
                clubId,
            )

        tipos shouldBe listOf("Empty")
    }

    @Test
    fun `reejecutar la siembra no duplica ejes ni valores`() {
        val ejesAntes = contarEjes()
        val valoresAntes = contarValores()

        jdbc.execute(migracionDeSiembra())

        contarEjes() shouldBe ejesAntes
        contarValores() shouldBe valoresAntes
    }

    private fun leerTaxonomia(): Taxonomy = taxonomyRepository.findByClub(ClubId.of(clubId))

    private fun valoresDe(
        taxonomy: Taxonomy,
        nombreDelEje: String,
    ): List<String> =
        taxonomy.keys
            .firstOrNull { it.label.value == nombreDelEje }
            .shouldNotBeNull()
            .values
            .map { it.label.value }

    private fun contarEjes(): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.tag_key WHERE club_id = ?",
            Int::class.java,
            clubId,
        )!!

    private fun contarValores(): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.tag_value WHERE club_id = ?",
            Int::class.java,
            clubId,
        )!!

    /**
     * El cuerpo real de la migración, no una copia: Flyway no la reaplica nunca, así que la única forma de ejercitar
     * de verdad su `ON CONFLICT` es volver a ejecutarla a mano. Una copia en el test se desincronizaría en silencio.
     */
    private fun migracionDeSiembra(): String =
        ClassPathResource("db/migration/club_taxonomia/V202607300001__siembra_taxonomia_por_defecto.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }
}
