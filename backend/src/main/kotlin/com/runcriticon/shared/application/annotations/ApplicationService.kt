package com.runcriticon.shared.application.annotations

import org.springframework.stereotype.Service

/**
 * Marca un caso de uso de la capa `application`. Equivale a un `@Service` de Spring (es un meta-estereotipo, así que se
 * escanea igual), pero además es el gancho que usan los ArchUnit tests para exigir que **todo** `@ApplicationService`
 * consulte autorización antes de tocar el dominio.
 *
 * Regla verificada: una clase `@ApplicationService` debe invocar la matriz de autorización (vía [AuthorizationMatrix]
 * /`AutorizacionService`) en cada método público de caso de uso, o declararse explícitamente exenta.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class ApplicationService
