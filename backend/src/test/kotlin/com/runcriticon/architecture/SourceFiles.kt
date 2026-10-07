package com.runcriticon.architecture

import java.io.File

private val PACKAGE_DECLARATION = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)

/** `null` si el fichero no declara paquete (no debería pasar en `src/main/kotlin`). */
fun File.packageDeclaration(): String? = PACKAGE_DECLARATION.find(readText())?.groupValues?.get(1)

/** Todos los `.kt` de producción del backend, para los guards que escanean texto en vez de bytecode. */
fun kotlinSourceFiles(): List<File> =
    File("src/main/kotlin")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
