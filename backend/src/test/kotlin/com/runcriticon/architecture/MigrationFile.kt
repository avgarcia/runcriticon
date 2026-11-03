package com.runcriticon.architecture

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Reglas de las migraciones Flyway (ADR-0004 D9) como funciones puras sobre nombres y texto, para poder probarlas con
// entradas sintéticas (FlywayMigrationsConventionTest) además de aplicarlas a las migraciones reales.

/** Una migración leída del disco: la carpeta de módulo donde vive, su nombre de fichero y su contenido. */
data class MigrationFile(
    val folder: String,
    val name: String,
    val text: String,
) {
    val path: String get() = "$folder/$name"
}

/**
 * Versión `V{YYYYMMDD}{NNNN}` de las migraciones a partir de la cual se exige el comentario `-- Rollback:`.
 *
 * Es la última versión que existía cuando se introdujo el guarda. Las anteriores quedan exentas porque ya están
 * aplicadas y editarlas cambia su checksum: Flyway se negaría a arrancar sobre cualquier base ya migrada. Solo se
 * puede subir si una migración anterior a ella cambia de verdad, nunca para eximir una nueva.
 */
const val ROLLBACK_REQUIRED_AFTER = 202_611_030_001L

private val MIGRATION_NAME = Regex("""^V(\d{8})(\d{4})__[a-z0-9_]+\.sql$""")
private val ROLLBACK_COMMENT = Regex("""(?m)^\s*--\s*Rollback:[ \t]*(\S.*)$""")

/** Todas las migraciones de las carpetas de módulo de `db/migration`, cada una con la carpeta a la que pertenece. */
fun migrationFiles(root: File = File("src/main/resources/db/migration")): List<MigrationFile> =
    root
        .listFiles { file -> file.isDirectory }
        .orEmpty()
        .sortedBy { it.name }
        .flatMap { folder ->
            folder
                .listFiles { file -> file.isFile }
                .orEmpty()
                .sortedBy { it.name }
                .map { MigrationFile(folder.name, it.name, it.readText()) }
        }

/**
 * Motivo por el que [name] no sigue `V{YYYYMMDD}{NNNN}__descripcion.sql`, o `null` si es conforme. La fecha debe
 * existir: `V202613010001` casa con los dígitos pero ordenaría una migración en un mes que no hay.
 */
fun nameViolation(name: String): String? {
    val match =
        MIGRATION_NAME.matchEntire(name)
            ?: return "no sigue V{YYYYMMDD}{NNNN}__descripcion.sql (descripción en minúsculas, dígitos y '_')"
    return try {
        LocalDate.parse(match.groupValues[1], DateTimeFormatter.BASIC_ISO_DATE)
        null
    } catch (_: DateTimeParseException) {
        "la fecha ${match.groupValues[1]} no existe"
    }
}

/** La versión numérica `YYYYMMDDNNNN` de un nombre conforme; `null` si no lo es. */
fun versionOf(name: String): Long? =
    MIGRATION_NAME.matchEntire(name)?.let { it.groupValues[1].toLong() * 10_000 + it.groupValues[2].toLong() }

/**
 * Versiones que llevan más de una migración, con los ficheros que la comparten. Flyway aplica todas las carpetas desde
 * un único historial: dos migraciones con la misma versión en carpetas distintas chocan igual (ADR-0004 D9).
 */
fun duplicateVersions(files: List<MigrationFile>): Map<Long, List<String>> =
    files
        .mapNotNull { file -> versionOf(file.name)?.let { it to file.path } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size > 1 }

/**
 * Motivo por el que [file] incumple el comentario de rollback, o `null` si lo cumple o está exenta. Debe llevar una
 * línea `-- Rollback: <texto>` con texto: vale «no reversible: …», pero no un comentario vacío.
 */
fun rollbackViolation(
    file: MigrationFile,
    requiredAfter: Long = ROLLBACK_REQUIRED_AFTER,
): String? {
    val version = versionOf(file.name)
    return when {
        version == null || version <= requiredAfter -> null
        ROLLBACK_COMMENT.containsMatchIn(file.text) -> null
        else -> "no lleva una línea `-- Rollback: <texto>`"
    }
}
