package com.runcriticon.shared.rgpd

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Table
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Exige que toda tabla de la aplicación declare su categoría RGPD (ADR-0014 D5) en el catálogo de PostgreSQL, con
 * `COMMENT ON TABLE <esquema>.<tabla> IS 'RGPD: <CATEGORÍA> — <motivo>'`. `RgpdArchTest` solo alcanza a las `@Entity`,
 * y la mayoría de las tablas se persisten por JDBC —entre ellas las de datos de salud de `seguimiento`—: su categoría
 * vivía en comentarios `--` de la migración que nada comprobaba.
 *
 * Lee el catálogo y no los ficheros SQL, así que cuenta cada tabla que existe tras aplicar todas las migraciones, se
 * haya creado como se haya creado. Además cruza el comentario con la [RgpdCategory] de cada entidad JPA, para que las
 * dos declaraciones no puedan divergir.
 */
class RgpdTableCategoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `encuentra las tablas de los seis esquemas, o la regla no comprobaria nada`() {
        tableComments().keys.map { it.substringBefore('.') }.toSet() shouldContainAll APPLICATION_SCHEMAS
    }

    @Test
    fun `toda tabla declara una categoria RGPD valida en su comentario`() {
        val violations =
            tableComments()
                .filterValues { categoryIn(it) == null }
                .map { (table, comment) -> "$table: ${comment?.let { "«$it»" } ?: "sin comentario"}" }

        withClue("$HOW_TO_FIX\n${violations.joinToString("\n")}") { violations.shouldBeEmpty() }
    }

    @Test
    fun `el comentario de cada tabla con entidad JPA coincide con su @RgpdCategory`() {
        val comments = tableComments()
        val mismatches =
            entityManagerFactory.metamodel.entities.mapNotNull { entity ->
                val type = entity.javaType
                val table = type.getAnnotation(Table::class.java)
                val declared = type.getAnnotation(RgpdCategory::class.java)?.category
                val name = table?.let { "${it.schema}.${it.name}" }
                val commented = name?.let { categoryIn(comments[it]) }
                if (name != null && declared == commented) {
                    null
                } else {
                    "${type.simpleName} (${name ?: "sin @Table con esquema"}): @RgpdCategory = $declared, " +
                        "comentario de la tabla = $commented"
                }
            }

        withClue(
            "La categoría de la @Entity y la del COMMENT ON TABLE de su tabla deben ser la misma:\n" +
                mismatches.joinToString("\n"),
        ) {
            mismatches.shouldBeEmpty()
        }
    }

    /** Cada tabla de los esquemas de la aplicación, como `esquema.tabla`, con su comentario (o `null` si no tiene). */
    private fun tableComments(): Map<String, String?> =
        jdbc
            .query(
                """
                SELECT n.nspname || '.' || c.relname AS table_name, obj_description(c.oid, 'pg_class') AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relkind IN ('r', 'p')
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema') AND n.nspname NOT LIKE 'pg\_%'
                  AND NOT (n.nspname = 'public' AND c.relname = 'flyway_schema_history')
                ORDER BY 1
                """.trimIndent(),
            ) { rs, _ -> rs.getString("table_name") to rs.getString("comment") }
            .toMap()

    private companion object {
        val APPLICATION_SCHEMAS =
            setOf("public", "identidad", "club_taxonomia", "planificacion", "seguimiento", "auditoria")

        /** `RGPD: <CATEGORÍA> — <motivo>`: la categoría es una constante de [Category] y el motivo no va vacío. */
        val COMMENT_FORMAT = Regex("""^RGPD: ([A-Z_]+) — \S.*""", RegexOption.DOT_MATCHES_ALL)

        fun categoryIn(comment: String?): Category? =
            comment
                ?.let { COMMENT_FORMAT.matchEntire(it) }
                ?.let { match -> Category.entries.firstOrNull { it.name == match.groupValues[1] } }

        val HOW_TO_FIX =
            "Toda tabla declara su categoría RGPD (ADR-0014 D5) en el catálogo. Añade, en la migración que la " +
                "crea o en una nueva de la carpeta de su módulo, " +
                "COMMENT ON TABLE <esquema>.<tabla> IS 'RGPD: <CATEGORÍA> — <por qué>'; con una de " +
                "${Category.entries.joinToString { it.name }}. Si la tabla tiene @Entity, la categoría es la de su " +
                "@RgpdCategory. Tablas sin una categoría válida (o sin motivo):"
    }
}
