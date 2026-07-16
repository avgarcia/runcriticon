package com.runcriticon.shared.observability

/**
 * Traduce el paquete raíz de un bounded context al tag `module`: debe coincidir con el esquema SQL del módulo, no con
 * el paquete Kotlin — `club_taxonomia` diverge porque su paquete raíz va sin guion bajo (`clubtaxonomia`, requisito de
 * ktlint/detekt para nombres de paquete). El resto de módulos (`identidad`, `planificacion`, `seguimiento`,
 * `auditoria`) coincide por casualidad. Usado tanto para eventos ([MdcRestorerForEvents]) como para peticiones HTTP
 * ([HttpMdcFilter]).
 */
internal object ModuleTagResolver {
    private val SCHEMA_BY_PACKAGE = mapOf("clubtaxonomia" to "club_taxonomia")

    /** `com.runcriticon.clubtaxonomia` → `club_taxonomia`; `com.runcriticon.identidad` → `identidad`. */
    fun resolve(rootPackage: String): String = SCHEMA_BY_PACKAGE[rootPackage] ?: rootPackage
}
