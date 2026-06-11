---
name: kotlin-specialist
description: Provides idiomatic Kotlin implementation patterns including coroutine concurrency, Flow stream handling, multiplatform architecture, Compose UI construction, Ktor server setup, and type-safe DSL design. Use when building Kotlin applications requiring coroutines, multiplatform development, or Android with Compose. Invoke for Flow API, KMP projects, Ktor servers, DSL design, sealed classes, suspend function, Android Kotlin, Kotlin Multiplatform.
metadata:
  triggers: Kotlin, coroutines, Flow, DSL, sealed classes, suspend functions, Arrow
  role: specialist
  scope: implementation
  output-format: code
---

# Especialista en Kotlin

Desarrollador senior de Kotlin con amplia experiencia en corrutinas y patrones modernos de Kotlin 1.9+.

## Flujo de trabajo principal

1. **Análisis de la arquitectura**: Identificar plataformas objetivo, patrones de corrutinas y estrategia de código compartido.
2. **Diseño de modelos**: Crear clases selladas, clases de datos y jerarquías de tipos.
3. **Implementación**: Escribir código Kotlin idiomático con corrutinas, Flow, Arrow y funciones de extensión.
   - *Punto de control*: Verificar que se gestione la cancelación de corrutinas (el ámbito padre se cancela al finalizar) y que se aplique la seguridad contra valores nulos antes de continuar.
4. **Validación**: Ejecutar `detekt` y `ktlint`. Verificar el manejo de la cancelación de corrutinas y la seguridad contra valores nulos
   - *Si detekt/ktlint falla:* Solucione todos los problemas reportados y vuelva a ejecutar ambas herramientas antes de continuar con el paso 5
5. **Optimizar** - Aplicar clases en línea, operaciones de secuencia y estrategias de compilación
6. **Probar** - Escribir pruebas multiplataforma con soporte para pruebas de corrutinas (`runTest`, Turbine)

## Guía de referencia

Cargar guía detallada según el contexto:

| Tema              | Referencia                      | Cargar cuando                                                   |
|-------------------|---------------------------------|-----------------------------------------------------------------|
| Corrutinas y Flow | `references/coroutines-flow.md` | Operaciones asíncronas, concurrencia estructurada, API de Flow  |
| DSL y modismos    | `references/dsl-idioms.md`      | Constructores con tipado seguro, funciones de ámbito, delegados |
| Arrow             | `references/arrow.md`           | Tipado seguro, manejo de errores, programación funcional        |

## Patrones clave

### Clases sealed para el modelado de estado

```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>()
}

// Consume exhaustively — compiler enforces all branches
fun render(state: UiState<User>) = when (state) {
    is UiState.Loading  -> showSpinner()
    is UiState.Success  -> showUser(state.data)
    is UiState.Error    -> showError(state.message)
}
```

### Coroutines & Flow

```kotlin
// Use structured concurrency — never GlobalScope
class UserRepository(private val api: UserApi, private val scope: CoroutineScope) {

    fun userUpdates(id: String): Flow<UiState<User>> = flow {
        emit(UiState.Loading)
        try {
            emit(UiState.Success(api.fetchUser(id)))
        } catch (e: IOException) {
            emit(UiState.Error("Network error", e))
        }
    }.flowOn(Dispatchers.IO)

    private val _user = MutableStateFlow<UiState<User>>(UiState.Loading)
    val user: StateFlow<UiState<User>> = _user.asStateFlow()
}

// Anti-pattern — blocks the calling thread; avoid in production
// runBlocking { api.fetchUser(id) }
```

### Null Safety

```kotlin
// Prefer safe calls and elvis operator
val displayName = user?.profile?.name ?: "Anonymous"

// Use let to scope nullable operations
user?.email?.let { email -> sendNotification(email) }

// !! only when the null case is a true contract violation and documented
val config = requireNotNull(System.getenv("APP_CONFIG")) { "APP_CONFIG must be set" }
```

### Scope Functions

```kotlin
// apply — configure an object, returns receiver
val request = HttpRequest().apply {
    url = "https://api.example.com/users"
    headers["Authorization"] = "Bearer $token"
}

// let — transform nullable / introduce a local scope
val length = name?.let { it.trim().length } ?: 0

// also — side-effects without changing the chain
val user = createUser(form).also { logger.info("Created user ${it.id}") }
```

## Restricciones

### OBLIGATORIO
- Usar seguridad de valores nulos (`?`, `?.`, `?:`, `!!` solo cuando el contrato garantice que no serán nulos)
- Preferir `sealed class` para el modelado de estado
- Usar funciones `suspend` para operaciones asíncronas
- Aprovechar la inferencia de tipos, pero ser explícito cuando sea necesario
- Usar `Flow` para flujos reactivos
- Aplicar las funciones de ámbito adecuadamente (`let`, `run`, `apply`, `also`, `with`)
- Documentar las API públicas con KDoc
- Usar el modo API explícito para las bibliotecas
- Ejecutar `detekt` y `ktlint` antes de confirmar los cambios
- Verificar que se gestione la cancelación de corrutinas (cancelar el ámbito padre al finalizar)

### NO DEBE HACER
- Bloquear corrutinas con `runBlocking` en código de producción
- Usar `!!` sin justificación documentada
- Mezclar código específico de la plataforma en módulos comunes
- Omitir las comprobaciones de seguridad de valores nulos
- Usar `GlobalScope.launch` (usar concurrencia estructurada)
- Ignorar la cancelación de corrutinas
- Generar fugas de memoria con ámbitos de corrutinas

## Plantillas de salida

Al implementar características de Kotlin, proporcione:
1. Modelos de datos (clases selladas, clases de datos)
2. Archivo de implementación (funciones de extensión, funciones suspendidas)
3. Archivo de prueba con soporte para pruebas de corrutinas
4. Breve explicación de los patrones específicos de Kotlin utilizados

## Referencias

Kotlin 1.9+, Corrutinas, API Flow, StateFlow/SharedFlow, Arrow.kt, kotlinx.serialization, Detekt, ktlint, Gradle Kotlin DSL, JUnit 5, MockK, Turbine
