package com.runcriticon.clubtaxonomia.domain.group

/**
 * Por qué está un alumno en el grupo **hoy**.
 *
 * Los dos valores son excluyentes y se calculan en cada lectura, no se guardan: quien cumple el filtro es [FILTER]
 * aunque además tenga una excepción de inclusión guardada, porque seguiría en el grupo si esa excepción se borrase.
 * Esa fila no sobra —es **latente**—: el día que el alumno pierda el tag, la misma consulta lo devolverá como
 * [MANUAL_INCLUSION] sin que nadie escriba nada. Es lo que hace que una excepción prevalezca sobre los cambios
 * posteriores de la clasificación.
 *
 * Mantener los dos valores disjuntos es también lo que permite el desglose de la pantalla —los del filtro, menos los
 * excluidos, más los incluidos a mano— sin contar a nadie dos veces.
 */
enum class GroupMemberOrigin {
    FILTER,
    MANUAL_INCLUSION,
}

/**
 * Miembro del grupo con el motivo por el que lo es.
 *
 * [hasOverride] responde una pregunta distinta de [origin]: si hay una excepción guardada que se pueda quitar. Sin él,
 * una inclusión manual sobre alguien que ya cumple el filtro sería indistinguible de un miembro cualquiera y no habría
 * forma de ofrecer el borrado de esa fila.
 *
 * Envuelve a [GroupMember] en vez de ampliarlo: la previsualización de un filtro todavía sin grupo no tiene origen del
 * que hablar, y heredaría un campo sin sentido.
 */
data class GroupMembership(
    val member: GroupMember,
    val origin: GroupMemberOrigin,
    val hasOverride: Boolean,
)

/**
 * Alumno sacado del grupo a mano. **No** es miembro y no cuenta en [GroupDetail.total].
 *
 * [matchesFilter] distingue la exclusión que de verdad lo está dejando fuera de la que hoy no cambia nada, porque el
 * alumno tampoco cumple el filtro; esta última sigue guardada y volverá a morder si algún día lo cumple.
 */
data class GroupExclusion(
    val member: GroupMember,
    val matchesFilter: Boolean,
)

/**
 * Composición actual de un grupo: quién está dentro y por qué, y a quién se ha sacado a mano.
 *
 * No es un agregado ni se guarda: es el resultado de resolver la membresía en el momento de leerla. Por eso [Group]
 * sigue sin conocer las excepciones —la regla que las combina con el filtro vive en la consulta de persistencia— y
 * aquí solo se transporta el resultado.
 */
data class GroupDetail(
    val group: Group,
    val members: List<GroupMembership>,
    val exclusions: List<GroupExclusion>,
) {
    val total: Int get() = members.size
}
