package com.runcriticon.shared.autorizacion.spring

import org.springframework.security.access.AccessDeniedException

/**
 * La matriz de autorización rechaza al principal en la primera reja, antes de llegar al caso de uso
 * ([AuthorizeInterceptor], ADR-0009 D2). `GlobalRestExceptionHandler` la traduce al 403 neutro de cualquier
 * denegación, con el mismo cuerpo que devuelven los casos de uso. Extiende [AccessDeniedException] para que, si se
 * escapara de ese handler, Spring Security respondiera igualmente 403 y nunca 500.
 */
class AuthorizeDeniedException(
    message: String,
) : AccessDeniedException(message)
