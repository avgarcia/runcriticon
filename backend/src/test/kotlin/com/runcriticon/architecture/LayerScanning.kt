package com.runcriticon.architecture

/**
 * Detección por texto de `application` importando `infrastructure` (ADR-0008 D3). Complementa, no
 * sustituye, la regla ArchUnit de bytecode de `CapasArchTest`: un acceso vía un `const val` (p. ej. un
 * literal `Qualifier` que referencia una constante de una clase de `infrastructure`) se **inlinea** en
 * tiempo de compilación — el bytecode resultante no conserva ninguna referencia a la clase de
 * `infrastructure`, así que esa violación es invisible para cualquier análisis de bytecode, no solo para
 * el de hoy (verificado con `javap -v` sobre una clase real que tenía este defecto).
 */
private val IMPORT_INFRAESTRUCTURA =
    Regex("""^import\s+(com\.runcriticon\.\w+\.infrastructure\.[\w.]+)""", RegexOption.MULTILINE)

fun importsDeInfraestructuraEnApplication(
    packageDeclaration: String,
    sourceText: String,
): List<String> {
    if (!packageDeclaration.contains(".application.") && !packageDeclaration.endsWith(".application")) {
        return emptyList()
    }
    return IMPORT_INFRAESTRUCTURA.findAll(sourceText).map { it.groupValues[1] }.toList()
}
