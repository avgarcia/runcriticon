package com.runcriticon.shared.autorizacion.spring

import org.springframework.security.access.AccessDeniedException

/**
 * El invariante que [AuthScopeEnforcementAspect] verifica se ha roto: un método `@AuthScope(CLUB)` se invocó sin
 * principal, sin parámetro `clubId`, o con un `clubId` distinto del principal. Extiende [AccessDeniedException] para
 * que el filtro de Spring Security la traduzca a 403 con cuerpo neutro — es un bug o un ataque, no flujo de dominio.
 */
class AuthScopeViolationException(
    message: String,
) : AccessDeniedException(message)
