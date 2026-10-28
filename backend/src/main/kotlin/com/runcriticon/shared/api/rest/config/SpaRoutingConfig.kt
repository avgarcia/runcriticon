package com.runcriticon.shared.api.rest.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Sirve la SPA de Angular desde la propia app, en el mismo origen que `/api` (ADR-0001): los estáticos que el
 * `Dockerfile` copia a `classpath:/static/` y, para cualquier otra ruta, `index.html`, para que un enlace profundo
 * (`/mi-plan`, el enlace de activación de un email) o una recarga los resuelva el router del navegador.
 *
 * Quién puede pedirlos lo decide `SecurityConfig` (`GET` fuera de `/api` y `/actuator` es público); aquí solo se
 * decide qué se devuelve. Al registrar el patrón de todas las rutas se sustituye el manejador de estáticos por
 * defecto de Spring Boot.
 */
@Configuration
class SpaRoutingConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(SpaFallbackResourceResolver())
    }
}

/**
 * El fichero pedido si existe; si no, `index.html`, salvo en tres casos que deben acabar en 404:
 *  - rutas de `/api` o `/actuator` sin controlador: devolver HTML con un 200 a un cliente de la API lo confundiría;
 *  - rutas con extensión (un `chunk-*.js` de un despliegue anterior): el navegador cargaría el HTML como JavaScript.
 */
internal class SpaFallbackResourceResolver : PathResourceResolver() {
    override fun getResource(
        resourcePath: String,
        location: Resource,
    ): Resource? {
        super.getResource(resourcePath, location)?.let { return it }
        val isBackendPath = resourcePath.substringBefore('/') in BACKEND_ROOTS
        val isFile = resourcePath.substringAfterLast('/').contains('.')
        return if (isBackendPath || isFile) null else super.getResource(INDEX, location)
    }

    private companion object {
        const val INDEX = "index.html"
        val BACKEND_ROOTS = setOf("api", "actuator")
    }
}
