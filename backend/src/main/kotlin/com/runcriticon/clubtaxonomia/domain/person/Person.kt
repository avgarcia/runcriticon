package com.runcriticon.clubtaxonomia.domain.person

import com.runcriticon.shared.tenancy.ClubId

/**
 * Persona del club (alumno o entrenador) tal como este módulo la conoce: el reflejo local de un usuario cuyo dueño es
 * `identidad`. Es el estado mínimo que hace falta para clasificar y agrupar sin llamadas síncronas a otro módulo —
 * nombre y email para poder pintar a la persona, rol y estado para filtrar.
 *
 * No es un agregado: este módulo no decide nada sobre ella y no tiene invariantes que imponer. Cambia solo cuando
 * llega un evento de integración que dice que cambió en su módulo dueño; las invariantes (email único en el club,
 * transiciones de estado legítimas) las impone `identidad` antes de publicar.
 */
data class Person(
    val id: PersonId,
    val clubId: ClubId,
    val name: String,
    val email: String,
    val role: PersonRole,
    val status: PersonStatus,
)
