package com.runcriticon.shared.rgpd

/**
 * Clasificación RGPD de los datos que persiste una entidad. Determina el tratamiento de borrado/retención de cada
 * categoría dentro del patrón de borrado mixto. Cada `@Entity` debe declarar su categoría (lo verifica `RgpdArchTest`),
 * y cada migración que crea una tabla con PII debe comentar la categoría correspondiente.
 */
enum class Category(
    val code: Int,
    val description: String,
) {
    /** Datos personales identificativos primarios del usuario (nombre, email, etc.). */
    PII_PRIMARIA(1, "PII primaria del usuario"),

    /** Rastro de auditoría del contexto de identidad (altas, bajas, cambios de credencial). */
    AUDITORIA_IDENTIDAD(2, "Auditoría de identidad"),

    /** Rastro de auditoría de decisiones de autorización y accesos sensibles. */
    AUDITORIA_AUTORIZACION(3, "Auditoría de autorización"),

    /** Eventos de integración en el outbox (`event_publication`). */
    OUTBOX(4, "Outbox de eventos"),

    /** Copias de seguridad. */
    BACKUPS(5, "Backups"),

    /** Logs operativos (sin PII en claro: usuarios seudonimizados). */
    LOGS_OPERATIVOS(6, "Logs operativos"),

    /** Datos sin información personal. */
    SIN_PII(0, "Sin PII"),
}

/**
 * Declara la [Category] RGPD de una entidad persistente. Obligatoria en toda `@Entity`; `RgpdArchTest` falla la build
 * si falta.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RgpdCategory(
    val category: Category,
)
