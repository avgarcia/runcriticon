package com.runcriticon.architecture

/**
 * Mapa paquete Kotlin → schema PostgreSQL (ADR-0004 D4). No son el mismo string: el paquete de
 * `club_taxonomia` es `clubtaxonomia` (sin guion bajo, `NamingConventionArchTest` lo exige en inglés/sin
 * separadores), el schema sí lleva guion bajo. Comparar paquete contra schema directamente (como hacía el
 * código antes de esta corrección) nunca casa para este módulo — es precisamente el módulo con más SQL vía
 * `JdbcTemplate` del backend.
 */
val PACKAGE_TO_SCHEMA =
    mapOf(
        "identidad" to "identidad",
        "clubtaxonomia" to "club_taxonomia",
        "planificacion" to "planificacion",
        "seguimiento" to "seguimiento",
        "auditoria" to "auditoria",
    )

val MODULE_SCHEMAS: Set<String> = PACKAGE_TO_SCHEMA.values.toSet()

/** `null` si el paquete no pertenece a ningún módulo conocido (p. ej. `shared`, `architecture`). */
fun String.schemaOfPackageOrNull(): String? =
    PACKAGE_TO_SCHEMA.entries
        .firstOrNull { (pkg, _) -> this == "com.runcriticon.$pkg" || startsWith("com.runcriticon.$pkg.") }
        ?.value

private val TRIPLE_QUOTED = Regex("\"\"\"([\\s\\S]*?)\"\"\"")

// Simplificado a "sin comilla ni backslash" (sin alternancia `(?:a|b)*`): la alternancia por escape
// provoca backtracking catastrófico (StackOverflowError) en ficheros con muchas comillas. No distinguir
// comillas escapadas es una simplificación aceptable para un escáner heurístico, no un parser completo.
private val DOUBLE_QUOTED = Regex("\"([^\"]*)\"")
private val SQL_KEYWORD = Regex("(?i)\\b(SELECT|INSERT|UPDATE|DELETE)\\b")

/**
 * Literales de cadena de un fichero fuente que *parecen* SQL (contienen una palabra clave DML/DQL). Solo mira
 * dentro de literales de cadena — nunca en comentarios/KDoc ni en líneas `import` — para no confundir una
 * mención en prosa (p. ej. el KDoc de un job de retención citando la tabla de otro módulo) con una consulta
 * real.
 */
fun sqlLikeStringLiterals(sourceText: String): List<String> {
    val literales =
        TRIPLE_QUOTED.findAll(sourceText).map { it.groupValues[1] } +
            DOUBLE_QUOTED.findAll(sourceText).map { it.groupValues[1] }
    return literales.filter { SQL_KEYWORD.containsMatchIn(it) }.toList()
}

/**
 * Schemas de **otros** módulos referenciados (`\bschema.`) dentro de literales SQL de un fichero que
 * pertenece a `ownModule`. Vacío si el fichero no tiene SQL cross-schema.
 */
fun crossSchemaReferences(
    ownSchema: String,
    sourceText: String,
): Set<String> {
    val otherSchemas = MODULE_SCHEMAS - ownSchema
    val literales = sqlLikeStringLiterals(sourceText)
    return otherSchemas.filterTo(mutableSetOf()) { schema ->
        literales.any { Regex("\\b$schema\\.").containsMatchIn(it) }
    }
}
