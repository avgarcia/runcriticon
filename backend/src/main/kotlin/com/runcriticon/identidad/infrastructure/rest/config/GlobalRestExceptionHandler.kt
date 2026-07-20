package com.runcriticon.identidad.infrastructure.rest.config

import com.runcriticon.identidad.infrastructure.rest.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Advice global de errores de framework: JSON malformado, tipos inválidos en path/query, rutas o métodos inexistentes y
 * cualquier excepción no controlada se traducen al contrato `{code, field?, message}` con mensajes neutros — el detalle
 * de la excepción va al log, nunca al body. Los errores de dominio NO pasan por aquí: cada controller mapea su `Either`
 * con [com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse].
 *
 * Vive en `identidad.infrastructure.rest` porque el modelo
 * [com.runcriticon.identidad.infrastructure.rest.ErrorResponse] se genera en este paquete (openApiGenerate en
 * build.gradle.kts) y `shared` no puede depender de `identidad`.
 */
@RestControllerAdvice
class GlobalRestExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Body ilegible o JSON malformado. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadableBody(): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Cuerpo de la petición malformado")

    /** Tipo inválido en path o query (p. ej. un UUID que no parsea en un `@PathVariable`). */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun onTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Parámetro con formato inválido", field = ex.name)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun onMissingParameter(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Falta un parámetro obligatorio", field = ex.parameterName)

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun onNoRoute(): ResponseEntity<ErrorResponse> = respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso no encontrado")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onMethodNotSupported(): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Método no soportado")

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun onMediaTypeNotSupported(): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type no soportado")

    /** Red de seguridad: 500 neutro. El stacktrace va al log; el body no revela nada del fallo. */
    @ExceptionHandler(Exception::class)
    fun onUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        if (ex is AccessDeniedException || ex is AuthenticationException) throw ex
        log.error("Error no controlado en un handler REST", ex)

        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno")
    }

    private fun respond(
        status: HttpStatus,
        code: String,
        message: String,
        field: String? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                code = code,
                message = message,
                field = field,
            ),
        )
}
