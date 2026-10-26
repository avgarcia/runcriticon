package com.runcriticon.clubtaxonomia.application.usecases

/**
 * Recurso con el que `@AuditAccess(SALUD)` registra las lecturas que exponen el tag `estado = lesión`
 * ([com.runcriticon.clubtaxonomia.domain.taxonomy.InjuryAxis]) — el mismo para el listado de alumnos, sus tags y la
 * composición de un grupo, porque el dato de salud leído es siempre el mismo: la fila de `alumno_tag`.
 */
internal const val INJURY_TAG_RESOURCE = "alumno_tag"
