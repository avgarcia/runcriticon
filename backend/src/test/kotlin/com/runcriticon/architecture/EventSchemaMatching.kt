package com.runcriticon.architecture

/**
 * Compara el nombre de una clase de evento (PascalCase) contra el de un fichero (kebab-case) por
 * palabras significativas, no por una conversión mecánica 1:1. El corpus real no es mecánicamente
 * consistente: `EntrenadorAsignadoAGrupo` → `entrenador-asignado-a-grupo-v1.json` conserva la "a" como
 * palabra propia, pero `AccesoADatosSensibles` → `acceso-datos-sensibles-v1.json` la omite. Ambos casos
 * son reales (verificados contra `schemas/`); exigir una derivación exacta produciría un falso positivo
 * en uno de los dos. Comparar solo las palabras de más de una letra, en el mismo orden, resuelve ambos
 * sin necesitar un caso especial.
 */
private val PASCAL_CASE_SPLIT =
    Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

fun significantWordsFromPascalCase(name: String): List<String> =
    name.split(PASCAL_CASE_SPLIT).filter { it.length > 1 }.map { it.lowercase() }

fun significantWordsFromKebabCase(kebab: String): List<String> = kebab.split("-").filter { it.length > 1 }

/** Nombre de fichero sin la extensión `.json` ni el sufijo de versión (`-v1`, `-v2`, …). */
fun schemaFileStem(fileName: String): String = fileName.removeSuffix(".json").replace(Regex("-v\\d+$"), "")

fun eventNameMatchesSchemaFile(
    eventClassName: String,
    schemaFileName: String,
): Boolean =
    significantWordsFromPascalCase(eventClassName) ==
        significantWordsFromKebabCase(schemaFileStem(schemaFileName))
