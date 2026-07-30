package com.runcriticon.clubtaxonomia.domain.person

/**
 * Papel de una persona dentro del club, en los únicos dos valores que este módulo proyecta.
 *
 * No reutiliza `shared.autorizacion.model.Role` a propósito: ese enum incluye `ADMIN`, que es un rol de autorización
 * sin sitio en esta proyección —el módulo clasifica y agrupa alumnos, y asigna entrenadores a grupos— y admitirlo
 * obligaría a todo consumidor de la proyección a tratar un caso imposible. Los valores son los que se persisten en la
 * columna `rol`.
 */
enum class PersonRole {
    ENTRENADOR,
    ALUMNO,
}
