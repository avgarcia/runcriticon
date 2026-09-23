package com.runcriticon.shared.application.annotations

import org.springframework.stereotype.Service

/**
 * Marca un caso de uso de la capa `application`. Equivale a un `@Service` de Spring (es un meta-estereotipo, así que se
 * escanea igual), pero además es el gancho que usan los ArchUnit tests para exigir que **todo** `@ApplicationService`
 * consulte autorización antes de tocar el dominio.
 *
 * Regla verificada (`AuthorizationArchTest`, ADR-0009 D13): una clase `@ApplicationService` debe acceder directamente
 * a `AuthorizationMatrix` (en la propia clase o en una clase anidada), o declararse exenta a nivel de clase con
 * `@NoAuthRequired`/`@AuthenticatedOnly`. No hay un `AutorizacionService` intermedio: las reglas de relación viven en
 * los puertos de consulta del módulo (ADR-0009 D7).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class ApplicationService
