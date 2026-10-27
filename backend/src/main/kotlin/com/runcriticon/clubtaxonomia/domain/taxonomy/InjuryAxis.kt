package com.runcriticon.clubtaxonomia.domain.taxonomy

import com.runcriticon.clubtaxonomia.domain.tag.ReservedRole
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId

/**
 * El valor con papel [ReservedRole.LESION] y todos los valores de su eje: lo que necesita `LesionDeclaradaListener`
 * para marcar a un alumno como lesionado reemplazando el resto del eje, que es conceptualmente exclusivo (`estado`).
 *
 * Se localiza **por el papel reservado, no por el nombre**: un admin puede renombrar el valor o el eje sin que el
 * sistema deje de reconocerlo. Si lo archiva, sigue reconociéndose, pero deja de ser asignable y el listener no lo
 * asigna (falla cerrado), que es lo que pide quien lo archiva.
 *
 * @property axisValueIds todos los valores del eje del valor de lesión.
 * @property injuryValueId el valor con papel [ReservedRole.LESION].
 */
data class InjuryAxis(
    val axisValueIds: Set<TagValueId>,
    val injuryValueId: TagValueId,
)

/** Ver [InjuryAxis]. `null` si el club no tiene ningún valor con papel [ReservedRole.LESION]. */
fun Taxonomy.injuryAxis(): InjuryAxis? =
    keys.firstNotNullOfOrNull { key ->
        key.values
            .firstOrNull { it.reservedRole == ReservedRole.LESION }
            ?.let { injury -> InjuryAxis(key.values.mapTo(mutableSetOf()) { it.id }, injury.id) }
    }

/**
 * Los valores marcados como dato de salud (`TagValue.healthData`), **también los archivados**: un alumno que conserva
 * un valor archivado sigue llevando ese dato, y su lectura, su asignación y su retirada siguen las mismas reglas.
 */
fun Taxonomy.healthDataValueIds(): Set<TagValueId> =
    keys
        .flatMap { it.values }
        .filter { it.healthData }
        .mapTo(mutableSetOf()) { it.id }
