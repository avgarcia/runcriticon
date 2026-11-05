package com.runcriticon.shared.autorizacion.spring

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registra [AuthorizeInterceptor] para todas las rutas. El interceptor solo actúa sobre los handlers con `@Authorize`:
 * el resto (recursos estáticos de la SPA, `@NoAuthRequired`, `@AuthenticatedOnly`) pasa sin tocar.
 */
@Configuration
class AuthorizeWebConfig(
    private val authorizeInterceptor: AuthorizeInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authorizeInterceptor)
    }
}
