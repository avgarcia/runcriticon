package com.runcriticon.clubtaxonomia.infrastructure.persistence

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Verifica sobre Postgres real (Testcontainers) que las migraciones aplican correctamente: el índice único parcial
 * de `tag_key`/`tag_value` ignora mayúsculas/acentos/espacios (réplica SQL de `TagLabel.normalized`) usando la
 * función IMMUTABLE que envuelve `unaccent`, el archivado libera el nombre, y el índice de `alumno_tag` para la
 * resolución de membresía de grupo existe. Sin repositorio ni entidades JPA todavía: el test opera con SQL directo
 * sobre el esquema.
 */
class TaxonomySchemaIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val clubA = UUID.randomUUID()
    private val clubB = UUID.randomUUID()

    @Test
    fun `unicidad de tag_key ignora mayusculas, acentos y espacios dentro del mismo club`() {
        insertTagKey(UUID.randomUUID(), clubA, "Nivel")

        val ex =
            assertThrows<DataIntegrityViolationException> {
                insertTagKey(UUID.randomUUID(), clubA, "  níVEL  ")
            }
        ex.message.shouldContain("tag_key_club_nombre_uk")
    }

    @Test
    fun `el mismo nombre normalizado no choca entre clubes distintos`() {
        insertTagKey(UUID.randomUUID(), clubA, "Terreno")
        insertTagKey(UUID.randomUUID(), clubB, "Terreno")
    }

    @Test
    fun `archivar una tag_key libera su nombre para reutilizarlo`() {
        val archivada = UUID.randomUUID()
        insertTagKey(archivada, clubA, "Objetivo")
        jdbc.update(
            "UPDATE club_taxonomia.tag_key SET archivado_en = now() WHERE id = ?",
            archivada,
        )

        insertTagKey(UUID.randomUUID(), clubA, "Objetivo")
    }

    @Test
    fun `unicidad de tag_value es dentro del mismo tag_key, ignorando mayusculas, acentos y espacios`() {
        val key = UUID.randomUUID()
        insertTagKey(key, clubA, "Distancia")
        insertTagValue(UUID.randomUUID(), key, clubA, "5K")

        val ex =
            assertThrows<DataIntegrityViolationException> {
                insertTagValue(UUID.randomUUID(), key, clubA, " 5k ")
            }
        ex.message.shouldContain("tag_value_key_nombre_uk")
    }

    @Test
    fun `metadata de tag_value rechaza un tipo desconocido por el CHECK`() {
        val key = UUID.randomUUID()
        insertTagKey(key, clubA, "Objetivo carrera")

        val ex =
            assertThrows<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre, metadata)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    key,
                    clubA,
                    "Maratón Valencia",
                    """{"tipo": "NoExiste"}""",
                )
            }
        ex.message.shouldContain("tag_value_metadata_tipo_check")
    }

    @Test
    fun `metadata de tag_value por defecto es Empty`() {
        val key = UUID.randomUUID()
        val value = UUID.randomUUID()
        insertTagKey(key, clubA, "Estado")
        insertTagValue(value, key, clubA, "Activo")

        val metadata =
            jdbc.queryForObject(
                "SELECT metadata::text FROM club_taxonomia.tag_value WHERE id = ?",
                String::class.java,
                value,
            )
        metadata shouldBe """{"tipo": "Empty"}"""
    }

    @Test
    fun `el indice de alumno_tag para la resolucion de grupos existe`() {
        val indexName =
            jdbc.queryForObject(
                """
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'club_taxonomia' AND tablename = 'alumno_tag'
                  AND indexname = 'alumno_tag_tag_value_alumno_idx'
                """.trimIndent(),
                String::class.java,
            )
        indexName shouldBe "alumno_tag_tag_value_alumno_idx"
    }

    private fun insertTagKey(
        id: UUID,
        clubId: UUID,
        nombre: String,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            id,
            clubId,
            nombre,
        )
    }

    private fun insertTagValue(
        id: UUID,
        tagKeyId: UUID,
        clubId: UUID,
        nombre: String,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            id,
            tagKeyId,
            clubId,
            nombre,
        )
    }
}
