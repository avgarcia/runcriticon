package com.runcriticon.clubtaxonomia.domain.tag

/**
 * Papel fijo que el sistema da a un [TagValue], para reconocerlo sin depender de su nombre. Como mucho un valor por
 * papel y club (índice único en BD). Los nombres de las constantes son los valores persistidos en
 * `tag_value.rol_reservado`, en castellano como el resto de enums persistidos (ADR-0008 D4).
 */
enum class ReservedRole {
    /** El valor que `LesionDeclaradaListener` asigna cuando el alumno avisa de lesión. Siempre es dato de salud. */
    LESION,
}
