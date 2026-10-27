package com.runcriticon.clubtaxonomia.application.usecases

/**
 * Recurso con el que `@AuditAccess(SALUD)` registra las lecturas que exponen un valor marcado como dato de salud
 * (`TagValue.healthData`: `lesión`, `post-parto`) — el mismo para el listado de alumnos, sus tags y la composición de
 * un grupo, porque el dato leído es siempre el mismo: la fila de `alumno_tag`.
 */
internal const val HEALTH_TAG_RESOURCE = "alumno_tag"
