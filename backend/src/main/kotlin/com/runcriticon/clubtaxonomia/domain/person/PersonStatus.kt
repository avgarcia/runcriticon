package com.runcriticon.clubtaxonomia.domain.person

/**
 * Estado de la cuenta de una persona, reflejo del que gobierna `identidad`: invitada pero sin activar, o activa.
 *
 * Este módulo lo proyecta porque condiciona lo que la interfaz puede ofrecer sobre esa persona (a un alumno que aún no
 * ha activado su cuenta se le puede asignar tags, pero conviene distinguirlo en el listado). Los valores son los que
 * se persisten en la columna `estado`.
 */
enum class PersonStatus {
    INVITADO,
    ACTIVO,
}
