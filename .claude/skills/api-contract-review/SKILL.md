---
nombre: api-contract-review
descripción: Revisa los contratos de la API REST en cuanto a semántica HTTP, control de versiones, compatibilidad con versiones anteriores y coherencia de las respuestas. Úsala cuando el usuario solicite «revisar la API», «comprobar los puntos finales» o «revisión REST», o antes de publicar cambios en la API.
---

# Habilidad de revisión del contrato API

Audita el diseño de la API REST para verificar su corrección, coherencia y compatibilidad con la semántica HTTP.

## Cuándo utilizarla
- El usuario solicita «revisar esta API» / «comprobar endpoints REST»
- Antes de publicar cambios en la API
- Revisar PR con cambios en controladores
- Verificar compatibilidad hacia atrás

---

## Referencia rápida: problemas comunes

| Problema                   | Síntoma                             | Impacto                                                             |
|----------------------------|-------------------------------------|---------------------------------------------------------------------|
| Verbo HTTP incorrecto      | POST para una operación idempotente | Confusión, problemas de almacenamiento en caché                     |
| Falta de versionado        | `/users` en lugar de `/v1/users`    | Los cambios rompen la compatibilidad y afectan a todos los clientes |
| Entity leak                | Entidad JPA en la respuesta         | Expone información interna, riesgo N+1                              |
| 200 con error              | `{«status»: 200, “error”: «...»}`   | Rompe el manejo de errores                                          |
| Nomenclatura inconsistente | `/getUsers` frente a `/users`       | API difícil de aprender                                             |

---

## Semántica de los verbos HTTP

### Guía de selección de verbos

| Verbo  | Uso                        | Idempotente | Seguro | Cuerpo de la solicitud |
|--------|----------------------------|-------------|--------|------------------------|
| GET    | Recuperar recurso          | Sí          | Sí     | No                     |
| POST   | Crear nuevo recurso        | No          | No     | Sí                     |
| PUT    | Reemplazar todo el recurso | Sí          | No     | Sí                     |
| PATCH  | Actualización parcial      | No*         | No     | Sí                     |
| DELETE | Eliminar recurso           | Sí          | No     | Opcional               |

*PATCH puede ser idempotente dependiendo de la implementación

### Errores comunes

```java
// ❌ POST para recuperación
@PostMapping("/users/search")
public List<User> searchUsers(@RequestBody SearchCriteria criteria) { }

// ✅ GET con parámetros de consulta (o POST solo si criterios muy complejos)
@GetMapping("/users")
public List<User> searchUsers(
    @RequestParam String name,
    @RequestParam(required = false) String email) { }

// ❌ GET para cambio de estado
@GetMapping("/users/{id}/activate")
public void activateUser(@PathVariable Long id) { }

// ✅ POST o PATCH para cambio de estado
@PostMapping("/users/{id}/activate")
public ResponseEntity<Void> activateUser(@PathVariable Long id) { }

// ❌ POST para actualización idempotente
@PostMapping("/users/{id}")
public User updateUser(@PathVariable Long id, @RequestBody UserDto dto) { }

// ✅ PUT para reemplazo completo, PATCH para parcial
@PutMapping("/users/{id}")
public User replaceUser(@PathVariable Long id, @RequestBody UserDto dto) { }

@PatchMapping("/users/{id}")
public User updateUser(@PathVariable Long id, @RequestBody UserPatchDto dto) { }
```

## Versionado de API

### Estrategias de versionado

| Estrategia  | Ejemplo                               | Ventajas              | Desventajas               |
|-------------|---------------------------------------|-----------------------|---------------------------|
| Ruta URL    | `/v1/users`                           | Clara, enrutado fácil | URL cambia                |
| Header      | `Accept: application/vnd.api.v1+json` | URLs limpias          | Oculta, difícil de probar |
| Parámetro   | `/users?version=1`                    | Fácil de agregar      | Fácil de olvidar          |

### Recomendado: Ruta URL

```java
// ✅ Endpoints versionados
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 { }

@RestController
@RequestMapping("/api/v2/users")
public class UserControllerV2 { }

// ❌ Sin versionado
@RestController
@RequestMapping("/api/users")  // Los cambios rompen compatibilidad para todos
public class UserController { }
```

### Checklist de versionado
- [ ] Todos los endpoints versionados (no `/api/users`, sino `/api/v1/users`)
- [ ] Versiones deprecadas marcadas con `@Deprecated`

---

### Paginación

```java
// ❌ Sin paginación en colecciones
@GetMapping("/users")
public List<User> getAllUsers() {
    return userRepository.findAll();  // Podrían ser millones
}

// ✅ Con paginación
@GetMapping("/users")
public Page<UserResponse> getUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
    return userService.findAll(PageRequest.of(page, size));
}
```

---

## Códigos de Estado HTTP

### Códigos de Éxito

| Código         | Cuándo usar                        | Cuerpo de respuesta                |
|----------------|------------------------------------|------------------------------------|
| 200 OK         | GET, PUT, PATCH exitosos           | Recurso o resultado                |
| 201 Created    | POST exitoso (creado)              | Recurso creado + header Location   |
| 204 No Content | DELETE exitoso, PUT sin cuerpo     | Vacío                              |

### Códigos de Error

| Código             | Cuándo usar                                    | Error común               |
|--------------------|------------------------------------------------|---------------------------|
| 400 Bad Request    | Entrada inválida, validación fallida           | Usar para "no encontrado" |
| 401 Unauthorized   | No autenticado                                 | Confundir con 403         |
| 403 Forbidden      | Autenticado pero no autorizado                 | Usar 401 en su lugar      |
| 404 Not Found      | El recurso no existe                           | Usar 400 en su lugar      |
| 409 Conflict       | Duplicado, modificación concurrente            | Usar 400 en su lugar      |
| 422 Unprocessable  | Error semántico (sintaxis válida, no válida)   | Usar 400 en su lugar      |
| 500 Internal Error | Error inesperado del servidor                  | Exponer stack traces      |

---

## Formato de Respuesta y Manejo de Errores

### Estructura de Error Consistente

```java
// ✅ Respuesta estándar de error
public class ErrorResponse {
    private String code;        // Legible por máquina: "USER_NOT_FOUND"
    private String message;     // Legible por humanos: "Usuario con ID 123 no encontrado"
    private Instant timestamp;
    private String path;
    private List<FieldError> errors;  // Para errores de validación
}

// En GlobalExceptionHandler
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFound(
        ResourceNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.builder()
            .code("RESOURCE_NOT_FOUND")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build());
}
```

### Seguridad: No exponer detalles del error

```java
// ❌ Expone stack trace
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleAll(Exception ex) {
    return ResponseEntity.status(500)
        .body(ex.getStackTrace().toString());  // ¡Riesgo de seguridad!
}

// ✅ Mensaje genérico, detalles en logs del servidor
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
    log.error("Error inesperado", ex);  // Detalles completos en logs
    return ResponseEntity.status(500)
        .body(ErrorResponse.of("INTERNAL_ERROR", "Ocurrió un error inesperado"));
}
```

---

## Compatibilidad y Cambios en la API

### Cambios incompatibles

| Cambio                              | ¿Rompe?  | Migración                                       |
|-------------------------------------|----------|-------------------------------------------------|
| Eliminar endpoint                   | Sí       | Deprecar primero, eliminar en siguiente versión |
| Eliminar campo de respuesta         | Sí       | Mantener campo, retornar null/default           |
| Agregar campo requerido a solicitud | Sí       | Hacer opcional con default                      |
| Cambiar tipo de campo               | Sí       | Agregar nuevo campo, deprecar antiguo           |
| Renombrar campo                     | Sí       | Soportar ambos temporalmente                    |
| Cambiar ruta URL                    | Sí       | Redirigir antigua a nueva                       |

### Cambios compatibles (Seguros)

- Agregar campo opcional a solicitud
- Agregar campo a respuesta
- Agregar nuevo endpoint
- Agregar nuevo parámetro de consulta opcional

### Patrón de Deprecación

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 {

    @Deprecated
    @GetMapping("/by-email")  // Endpoint antiguo
    public UserResponse getByEmailOld(@RequestParam String email) {
        return getByEmail(email);  // Delegar a nuevo
    }

    @GetMapping(params = "email")  // Patrón nuevo
    public UserResponse getByEmail(@RequestParam String email) {
        return userService.findByEmail(email);
    }
}
```

---

## Checklist de Revisión de API

### 1. Semántica HTTP
- [ ] GET solo para recuperación (sin efectos secundarios)
- [ ] POST para creación (retorna 201 + Location)
- [ ] PUT para reemplazo completo (idempotente)
- [ ] PATCH para actualizaciones parciales
- [ ] DELETE para eliminación (idempotente)

### 2. Diseño de URL
- [ ] Versionada (`/v1/`, `/v2/`)
- [ ] Sustantivos, no verbos (`/users`, no `/getUsers`)
- [ ] Plural para colecciones (`/users`, no `/user`)
- [ ] Jerárquica para relaciones (`/users/{id}/orders`)
- [ ] Nomenclatura consistente (kebab-case en minúsculas)
- [ ] No utilizar verbos en el path (`/users/search`)

### 3. Manejo de Solicitudes
- [ ] Validación con `@Valid`
- [ ] Mensajes de error claros para fallos de validación
- [ ] DTOs de solicitud (no entidades)
- [ ] Límites de tamaño razonables

### 4. Diseño de Respuesta
- [ ] DTOs de respuesta (no entidades)
- [ ] Estructura consistente entre endpoints
- [ ] Paginación para colecciones con el formato `{ "data": [...], "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 }`
- [ ] Códigos de estado adecuados (no 200 para errores)

### 5. Manejo de Errores
- [ ] Formato de error consistente
- [ ] Códigos de error legibles por máquina
- [ ] Mensajes legibles por humanos
- [ ] Sin stack traces expuestos
- [ ] Distinción adecuada entre 4xx y 5xx

### 6. Compatibilidad
- [ ] Sin cambios incompatibles en versión actual
- [ ] No mezclar versiones en el mismo controlador
- [ ] Endpoints deprecados documentados
- [ ] Ruta de migración para cambios incompatibles

---

## Optimización de tokens

Para APIs grandes:
1. Listar todos los controladores: `find . -name "*Controller.java"`
2. Muestrear 2-3 controladores para análisis de patrones
3. Verificar la configuración de `@ExceptionHandler` una vez
4. Grep para patrones específicos anti:
   ```bash
   # Encontrar posibles entity leaks
   grep -r "public.*Entity.*@GetMapping" --include="*.java"

   # Encontrar patrones 200 con error
   grep -r "ResponseEntity.ok.*error" --include="*.java"

   # Encontrar APIs sin versionado
   grep -r "@RequestMapping.*api" --include="*.java" | grep -v "/v[0-9]"
   ```
