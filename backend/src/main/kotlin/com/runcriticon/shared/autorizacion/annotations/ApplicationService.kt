package com.runcriticon.shared.autorizacion.annotations

import org.springframework.stereotype.Service

/**
 * Marca un caso de uso de la capa `application` (ADR-0009 D6, guía operativa). Equivale a un
 * `@Service` de Spring (es un meta-estereotipo, así que se escanea igual), pero además es el
 * gancho que usan los ArchUnit tests para exigir que **todo** `@ApplicationService` consulte
 * autorización antes de tocar el dominio.
 *
 * Regla verificada: una clase `@ApplicationService` debe invocar la matriz de autorización
 * (vía [AuthorizationMatrix]/`AutorizacionService`) en cada método público de caso de uso, o
 * declararse explícitamente exenta. La verificación viva llega en Fase 1; en H0 queda el marcador.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class ApplicationService
