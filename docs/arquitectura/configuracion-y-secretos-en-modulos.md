# Configuración y secretos en módulos — guía de referencia

Subdocumento de [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md). Cubre los **detalles de configuración y secretos por módulo** que la guía principal resume: convención de nombres en SSM, `@ConfigurationProperties` tipado, inyección de env vars, runbooks de rotación, secretos en tests, bloqueo de SSM real desde local.

> Espejo aplicado de **ADR-0013** (configuración y secretos en runtime) y los puntos de contacto con ADR-0006 (App Runner inyecta env vars), ADR-0014 (DPA con AWS cubre SSM), ADR-0011 D13 (log levels via Actuator). Si hay conflicto, gana el ADR.

## 1. Propósito y alcance

Cada módulo es responsable de:

1. **Declarar sus propiedades de configuración** con `@ConfigurationProperties` tipado.
2. **Declarar sus secretos** en el catálogo central + runbook de rotación.
3. **Leer la configuración via `Environment` de Spring**, **nunca via SDK de AWS** (ADR-0013 D8).
4. **Proteger los entornos**: ningún módulo lee SSM real desde el perfil local.
5. **Documentar la convención de nombres** seguida en SSM.

## 2. Convención de nombres en SSM

Cruce con [ADR-0013 D5](../adr/0013-configuracion-y-secretos.md#d5). Todos los secretos del runtime siguen:

```
/runcriticon/{env}/{component}/{name}
```

| Parte | Valores |
|---|---|
| `{env}` | `staging` \| `production` |
| `{component}` | nombre del componente (`db`, `email`, `crypto`, `auditoria`, etc.) — habitualmente coincide con el módulo o con la dependencia externa |
| `{name}` | nombre canónico kebab-case |

Ejemplos por módulo:

| Módulo | Secreto en SSM | Cruce |
|---|---|---|
| Identidad | `/runcriticon/production/db/password` | ADR-0013 D6 |
| Identidad | `/runcriticon/production/security/token-hmac-secret` | ADR-0003 D13 |
| Email (transversal) | `/runcriticon/production/email/postmark-server-token` | ADR-0005 D1 |
| Email (transversal) | `/runcriticon/production/email/postmark-webhook-secret` | ADR-0005 D9 |
| Observabilidad | `/runcriticon/production/crypto/userid-hash-salt` | ADR-0011 D5, ADR-0014 D9 |

**Cualquier secreto fuera de esta convención no entra en producción** (ArchUnit + revisión PR).

## 3. Catálogo de secretos por módulo

Cada módulo declara explícitamente en su `CONFIG.md` los secretos que consume:

```markdown
# Configuración — módulo Identidad

## Secretos consumidos

| Secreto SSM | Variable de entorno | Tipo | Uso |
|---|---|---|---|
| /runcriticon/{env}/db/password | DB_PASSWORD | SecureString | Conexión RDS PostgreSQL |
| /runcriticon/{env}/security/token-hmac-secret | TOKEN_HMAC_SECRET | SecureString (256-bit hex) | HMAC de tokens de un solo uso y de email para rate-limiting (ADR-0003 D13) |
| /runcriticon/{env}/crypto/userid-hash-salt | USERID_HASH_SALT | SecureString | Hash determinístico de user_id para logs (ADR-0011 D5) |

## Propiedades no secretas

| Propiedad | Variable de entorno | Defecto | Uso |
|---|---|---|---|
| runcriticon.identidad.magic-link.ttl | MAGIC_LINK_TTL | PT15M | TTL del magic link (ADR-0003 D5) |
| runcriticon.identidad.session.ttl | SESSION_TTL | PT24H | TTL de la cookie de sesión |
| runcriticon.identidad.rate-limit.magic-link-per-hour | MAGIC_LINK_PER_HOUR | 3 | Rate limit (ADR-0003 D12) |
```

El catálogo + el del MVP (cruce ADR-0013 D6) son la fuente de verdad: cualquier secreto del runtime aparece en al menos uno.

### Secretos de semilla (staging únicamente)

`IdentidadSeeder` (`@Profile("local","staging")`, ADR-0003 D3) crea el primer admin del club al arranque a partir de una contraseña de bootstrap. **Producción nunca siembra credenciales**: este secreto vive en SSM **solo para `staging`** (en `local` no se lee SSM — §1, regla 4). Es un secreto **externo** (placeholder + `lifecycle.ignore_changes` en Terraform): el valor real se inyecta fuera de banda.

| Secreto en SSM | Variable de entorno | Tipo | Rotación |
|---|---|---|---|
| `/runcriticon/staging/identidad/bootstrap-admin-password` | `RUNCRITICON_BOOTSTRAP_ADMIN_PASSWORD` | SecureString | [`rotacion-bootstrap-admin-password.md`](../runbooks/rotacion-bootstrap-admin-password.md) |

> El path lleva `staging` fijo (no `{env}`) a propósito: deja inequívoco que no es un secreto de producción.

## 4. `@ConfigurationProperties` tipado por módulo

Cada módulo tiene una clase `{Modulo}Properties` en `infrastructure/config`. Tipado con Bean Validation.

### Patrón canónico

```kotlin
// identidad/infrastructure/config/IdentidadProperties.kt
@ConfigurationProperties(prefix = "runcriticon.identidad")
@Validated
data class IdentidadProperties(

    @field:Valid
    val magicLink: MagicLink = MagicLink(),

    @field:Valid
    val session: Session = Session(),

    @field:Valid
    val rateLimit: RateLimit = RateLimit(),
) {
    data class MagicLink(
        @field:NotNull val ttl: Duration = Duration.ofMinutes(15),
    )

    data class Session(
        @field:NotNull val ttl: Duration = Duration.ofHours(24),
    )

    data class RateLimit(
        @field:Min(1) val magicLinkPerHour: Int = 3,
        @field:Min(1) val magicLinkPerDay:  Int = 10,
        @field:Min(1) val resetPerHour:     Int = 3,
        @field:Min(1) val resetPerDay:      Int = 5,
    )
}
```

### Habilitación

```kotlin
// identidad/infrastructure/config/IdentidadConfig.kt
@Configuration
@EnableConfigurationProperties(IdentidadProperties::class)
class IdentidadConfig
```

### Mapeo desde `application.yml`

```yaml
runcriticon:
  identidad:
    magic-link:
      ttl: PT15M
    session:
      ttl: PT24H
    rate-limit:
      magic-link-per-hour: 3
      magic-link-per-day: 10
      reset-per-hour: 3
      reset-per-day: 5
```

> El secreto compartido `security/token-hmac-secret` (`TOKEN_HMAC_SECRET`) **no** se tipa dentro de `IdentidadProperties` — se inyecta con `@Value` directamente en el componente que lo usa (`TokenHasherImpl`, `EmailHasherImpl`, ambos en `infrastructure/security`), porque es un secreto del núcleo de seguridad compartido entre casos de uso, no una propiedad exclusiva de un módulo.

### Uso en casos de uso

```kotlin
@ApplicationService
class SolicitarMagicLinkService(
    private val magicLinkRepo: MagicLinkRepository,
    private val enviadorDeEmail: EnviadorDeEmail,
    private val tokenHasher: TokenHasher,
    private val properties: IdentidadProperties,
) {
    fun ejecutar(email: Email): Either<IdentidadError, Unit> = either {
        val raw = RawToken.aleatorio()
        val magicLink = MagicLink.nuevo(
            email = email,
            ttl = properties.magicLink.ttl,
            tokenHash = tokenHasher.hash(raw),  // solo el hash se persiste; el token en claro va por email
        )
        magicLinkRepo.guardar(magicLink)
        enviadorDeEmail.enviar(magicLink, raw).bind()
    }
}
```

### Por qué tipado

- **Validación en arranque**: si falta `MAGIC_LINK_TTL` o cualquier otra propiedad tipada, la app falla al arrancar (no en runtime). El secreto `TOKEN_HMAC_SECRET` tiene su propia validación fail-fast en `TokenHasherImpl`/`EmailHasherImpl` (`require(secret.isNotBlank())`).
- **Refactor seguro**: cambiar el nombre de una propiedad lo detecta el compilador.
- **Tests deterministas**: cada test instancia `IdentidadProperties(...)` con valores explícitos.
- **Catálogo legible**: la clase es la lista de propiedades del módulo.

## 5. Inyección de env vars por App Runner

Cruce con [ADR-0013 D7](../adr/0013-configuracion-y-secretos.md#d7). En la IaC de App Runner (Terraform, ADR-0006 D18), cada secreto se declara como referencia a SSM:

```hcl
# infrastructure/terraform/app_runner.tf
resource "aws_apprunner_service" "app" {
  service_name = "runcriticon-${var.env}"

  source_configuration {
    image_repository {
      image_configuration {
        runtime_environment_secrets = {
          DB_PASSWORD              = "arn:aws:ssm:eu-west-1:${var.account_id}:parameter/runcriticon/${var.env}/db/password"
          POSTMARK_SERVER_TOKEN    = "arn:aws:ssm:eu-west-1:${var.account_id}:parameter/runcriticon/${var.env}/email/postmark-server-token"
          POSTMARK_WEBHOOK_SECRET  = "arn:aws:ssm:eu-west-1:${var.account_id}:parameter/runcriticon/${var.env}/email/postmark-webhook-secret"
          TOKEN_HMAC_SECRET        = "arn:aws:ssm:eu-west-1:${var.account_id}:parameter/runcriticon/${var.env}/security/token-hmac-secret"
          USERID_HASH_SALT         = "arn:aws:ssm:eu-west-1:${var.account_id}:parameter/runcriticon/${var.env}/crypto/userid-hash-salt"
        }
        runtime_environment_variables = {
          SPRING_PROFILES_ACTIVE = var.env
          # Más variables no-secretas si aplica
        }
      }
    }
  }
}
```

App Runner inyecta los secretos como **variables de entorno** al arrancar el contenedor. La app las lee con `${ENV_VAR}` en `application.yml`.

## 6. Lectura sin SDK de AWS

ADR-0013 D8: el código del módulo **nunca** llama al SDK de AWS para leer secretos. Sólo `Environment` de Spring.

### Prohibido en código de módulo

```kotlin
// ❌ Prohibido en cualquier código de módulo
import software.amazon.awssdk.services.ssm.SsmClient

class MalEjemplo {
    fun lee() {
        val client = SsmClient.create()
        val value = client.getParameter { it.name("...").withDecryption(true) }
        // ...
    }
}
```

### ArchUnit guard

```kotlin
// test/architecture/ConfiguracionArchTest.kt
@ArchTest
val `ningun modulo importa el SDK de AWS para leer SSM` =
    noClasses().that().resideInAPackage("com.runcriticon..")
        .and().resideOutsideOfPackage("..shared.aws..")     // excepción solo en shared si fuera necesario
        .should().dependOnClassesThat().resideInAnyPackage(
            "software.amazon.awssdk.services.ssm..",
            "software.amazon.awssdk.services.secretsmanager..",
        )
```

El día que la plataforma cambie (otra nube, otro mecanismo de inyección), la app **no cambia** — el nuevo proveedor inyectará las env vars a su manera. Portabilidad real (cruce ADR-0006 D23).

## 7. Perfiles Spring por entorno

Cruce con [ADR-0013 D1, D2](../adr/0013-configuracion-y-secretos.md#d1).

### Estructura de archivos

```
backend/src/main/resources/
├── application.yml                  ← común (defaults aplicables a todos)
├── application-local.yml            ← perfil de desarrollo local
├── application-staging.yml          ← perfil de staging
├── application-production.yml       ← perfil de producción
└── application-test.yml             ← perfil de tests (src/test/resources, no main)
```

### `application.yml` (común)

```yaml
spring:
  application:
    name: runcriticon
  modulith:
    events:
      republish-outstanding-events-on-restart: true

runcriticon:
  identidad:
    magic-link:
      ttl: PT15M
    session:
      ttl: PT24H
```

### `application-production.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

runcriticon:
  security:
    token-hmac-secret: ${TOKEN_HMAC_SECRET}

logging:
  level:
    root: INFO
    com.runcriticon: INFO
```

### `application-local.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/runcriticon_local
    username: runcriticon
    password: local-dev-not-prod

runcriticon:
  security:
    token-hmac-secret: local-dev-only-not-for-prod-32-bytes-aaaa

email:
  postmark-server-token: POSTMARK_API_TEST    # sandbox público de Postmark
  smtp-host: localhost                        # MailHog levantado por docker-compose
  smtp-port: 1025

logging:
  level:
    com.runcriticon: DEBUG
```

**Prefijos visibles** (`local-dev-only-not-for-prod`, `POSTMARK_API_TEST`) hacen que cualquier escape accidental sea inmediatamente reconocible.

## 8. Cambio de log levels en runtime via Actuator

Cruce con [ADR-0013 D9](../adr/0013-configuracion-y-secretos.md#d9) (aclaración del cruce con ADR-0011 D13). **Único mecanismo** de cambio de configuración sin redeploy en MVP.

### Patrón

```bash
# Subir a DEBUG el módulo de Identidad en producción durante incidente
curl -X POST 'https://app.runcriticon.com/actuator/loggers/com.runcriticon.identidad' \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer $ADMIN_TOKEN' \
     -d '{"configuredLevel": "DEBUG"}'

# Restaurar
curl -X POST 'https://app.runcriticon.com/actuator/loggers/com.runcriticon.identidad' \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer $ADMIN_TOKEN' \
     -d '{"configuredLevel": null}'
```

### Restricciones

- **Endpoint protegido** con auth (rol `ADMIN`).
- **Accesible solo desde el VPC** (cruce ADR-0006 D11). No expuesto a Internet.
- **Auditado** en la auditoría de autorización del módulo `auditoria` con `@AuditaAcceso(TipoAcceso.OPERACION_CRITICA)` (cruce con [`rgpd-en-modulos.md`](rgpd-en-modulos.md) §5 ampliable).
- **Cualquier otro cambio de configuración** (URLs, dimensionado, rate limits) requiere **redeploy de App Runner**.

## 9. Política de rotación: runbooks por secreto

Cruce con [ADR-0013 D10, D11](../adr/0013-configuracion-y-secretos.md#d10). Cada secreto del catálogo tiene su runbook en `docs/runbooks/rotacion-{secreto}.md`.

### Plantilla del runbook

```markdown
# Runbook — rotación del secreto `security/token-hmac-secret`

## Frecuencia

Anual + ante sospecha de compromiso.

## Pre-requisitos

- Ventana de mantenimiento programada (~15 min).
- Equipo notificado en el canal de alertas.
- Acceso AWS CLI con MFA.

## Procedimiento

1. **Generar nuevo valor**:

   ```bash
   openssl rand -hex 32
   ```

   Copiar al portapapeles del **operador**, no de la consola compartida.

2. **Persistir en SSM**:

   ```bash
   aws ssm put-parameter \
     --name /runcriticon/production/security/token-hmac-secret \
     --value "$NUEVO_VALOR" \
     --type SecureString \
     --overwrite
   ```

3. **Redeploy de App Runner**:

   ```bash
   aws apprunner start-deployment --service-arn $APP_RUNNER_ARN
   ```

   App Runner toma ~5-10 min en aplicar (NFR < 10 min).

4. **Verificación**:
   - `/actuator/health` reporta `UP`.
   - Login de prueba con magic link funciona (genera y canjea un magic link nuevo tras la rotación).
   - **Importante**: cualquier token pendiente de canjear emitido antes de la rotación (invitación, magic link, reseteo de contraseña) deja de validar su hash — el usuario debe pedirlo de nuevo. **No afecta a sesiones activas** (Spring Session no usa este secreto).

5. **Revocar el valor antiguo**: no aplica (crypto, valor nuevo)

6. **Registrar en el log de rotaciones**: `docs/runbooks/log-rotaciones.md` con fecha, secreto, operador.

## Rollback

Si el deploy falla:

```bash
aws apprunner update-service --service-arn $ARN --source-configuration '...image_identifier=<imagen_anterior>'
```

(El valor anterior ya no existe en SSM; redesplegar la imagen anterior con el `TOKEN_HMAC_SECRET` nuevo sigue funcionando, solo se pierden los tokens pendientes de canjear emitidos con el valor viejo).
```

## 10. Secretos en tests

### `application-test.yml`

Valores fake con prefijos visibles que **nunca podrían pasar por reales**:

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///runcriticon_test     # Testcontainers JDBC URL
    username: test
    password: test

runcriticon:
  security:
    token-hmac-secret: test-only-not-for-prod-32-bytes-aaaaaaaaaaaa
  observabilidad:
    userid-hash-salt: test-only-not-for-prod-salt-cccccccccccccccc

email:
  postmark-server-token: POSTMARK_API_TEST              # sandbox público de Postmark
  webhook-secret: test-only-not-for-prod-dddddddddddd
```

### ArchUnit / convención de tests

```kotlin
@ArchTest
val `application-test_yml no tiene valores con shape de produccion` = customRule {
    val file = Path.of("src/test/resources/application-test.yml").readText()
    val sospechosos = listOf(
        Regex("[a-f0-9]{64}"),                          // hex de 32 bytes (cripto realista)
        Regex("postmark-server-[a-zA-Z0-9]{36}"),       // shape de token Postmark real
    )
    sospechosos.any { it.containsMatchIn(file) } shouldBe false
}
```

### Patrón `IntegrationTestBase` heredado de testing-de-modulos.md

```kotlin
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class IntegrationTestBase {
    // El perfil test carga automáticamente application-test.yml
    // Sin SDK de AWS, sin SSM, sin secretos reales
}
```

## 11. Bloqueo de SSM real desde local

Cruce con [ADR-0013 D13](../adr/0013-configuracion-y-secretos.md#d13). En el perfil `local`, el bean rechaza credenciales AWS reales.

### Bean de protección

```kotlin
// backend/src/main/kotlin/com/runcriticon/shared/config/LocalProfileGuard.kt
@Component
@Profile("local")
class LocalProfileGuard(private val env: Environment) {

    @PostConstruct
    fun verify() {
        val realAwsCredentials = listOf(
            env.getProperty("AWS_ACCESS_KEY_ID"),
            env.getProperty("AWS_SESSION_TOKEN"),
        ).filterNotNull().filter { it.length > MIN_REAL_CREDENTIAL_LENGTH }

        check(realAwsCredentials.isEmpty()) {
            """
            Credenciales AWS reales detectadas en perfil local.

            El perfil local NO debe acceder a SSM staging o producción.
            Razón: PII de entornos remotos no debe replicarse en máquinas locales (ADR-0013 D13).

            Soluciones:
            - Usa application-local.yml con valores fake.
            - Si necesitas un dato de staging para debugging, pásalo por canal seguro fuera de banda.
            - Si necesitas SSM real, usa el perfil staging o un compañero del equipo lo hace por ti.
            """.trimIndent()
        }
    }

    private companion object {
        const val MIN_REAL_CREDENTIAL_LENGTH = 16
    }
}
```

La app **rechaza arrancar** (`IllegalStateException` en `@PostConstruct`, envuelta por Spring en `BeanCreationException`) si detecta credenciales AWS reales en perfil local. `env.getProperty(...)` ya cubre variables de entorno del SO (Spring las expone como property source) — no hace falta duplicar con `System.getenv` directo.

### IAM policy adicional (defensa en profundidad)

En la cuenta AWS, los developers tienen una policy que **niega explícitamente** `ssm:GetParameter` sobre `/runcriticon/staging/*` y `/runcriticon/production/*` por defecto. Sólo roles específicos (admin con MFA) tienen acceso. Cruce ADR-0013 D15.

## 12. Tests obligatorios de configuración

### Test: `@ConfigurationProperties` se valida al arrancar

```kotlin
@SpringBootTest(properties = ["runcriticon.security.token-hmac-secret="])  // vacío
class ConfigValidacionTest {

    @Test
    fun `arranque falla si token-hmac-secret esta vacio`() {
        shouldThrow<BeanCreationException> {
            // TokenHasherImpl.init falla su require(secret.isNotBlank())
        }
    }
}
```

### Test: cambio de log level via Actuator

```kotlin
class LogLevelActuatorTest : IntegrationTestBase() {

    @Test
    fun `POST actuator loggers cambia el nivel del logger`() {
        mockMvc.perform(post("/actuator/loggers/com.runcriticon.identidad")
            .with(adminPrincipal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"configuredLevel": "DEBUG"}"""))
            .andExpect(status().isOk)

        val logger = LoggerFactory.getLogger("com.runcriticon.identidad") as Logger
        logger.level shouldBe Level.DEBUG
    }
}
```

### Test: el SDK de AWS no está en el código de módulo

ArchUnit guard ya cubierto en sección 6.

### Test: secretos no aparecen en logs

```kotlin
@SpringBootTest
class SecretosNoEnLogsTest {

    @Value("\${runcriticon.security.token-hmac-secret}")
    lateinit var tokenHmacSecret: String

    @Test
    fun `token-hmac-secret nunca se loguea`() {
        val logs = capturarLogs {
            magicLinkService.solicitar(emailValido())
        }

        logs.forEach { log ->
            log.formattedMessage shouldNotContain tokenHmacSecret
        }
    }
}
```

## 13. Checklist de configuración y secretos al crear un módulo

- [ ] `{Modulo}Properties` creado en `infrastructure/config` con `@ConfigurationProperties(prefix = "runcriticon.{modulo}")` y `@Validated` `(ADR-0013 D1)`
- [ ] Habilitado con `@EnableConfigurationProperties` en `{Modulo}Config` `(ADR-0013 D1)`
- [ ] Bean Validation aplicado: `@NotBlank`, `@NotNull`, `@Min`, `@Valid` para sub-objetos `(ADR-0013 D1)`
- [ ] `CONFIG.md` del módulo creado con tabla de **secretos consumidos** + **propiedades no secretas**
- [ ] Cada secreto del módulo en el catálogo central (ADR-0013 D6); ningún secreto fuera del catálogo
- [ ] Convención de nombres `/runcriticon/{env}/{component}/{name}` respetada `(ADR-0013 D5)`
- [ ] Inyección de env vars declarada en Terraform App Runner `(ADR-0013 D7)`
- [ ] El módulo lee via `Environment` de Spring, **nunca** via SDK de AWS `(ADR-0013 D8)`
- [ ] ArchUnit guard activo: no imports de `software.amazon.awssdk.services.ssm` ni `secretsmanager`
- [ ] `application-{profile}.yml` con valores correctos por entorno (local con fakes, prod con `${ENV_VAR}`) `(ADR-0013 D1, D2)`
- [ ] `application-local.yml` con valores fake con prefijo `local-dev-only-not-for-prod`
- [ ] `application-test.yml` con valores fake con prefijo `test-only-not-for-prod`
- [ ] Si el módulo introduce un secreto nuevo: runbook `docs/runbooks/rotacion-{secreto}.md` creado en la misma PR `(ADR-0013 D11)`
- [ ] `LocalProfileGuard` activo en perfil local (rechaza credenciales AWS reales) `(ADR-0013 D13)`
- [ ] Tests verifican: arranque falla con configuración incompleta, log level cambia via Actuator, secretos no aparecen en logs

## Referencias

- **ADR-0013 D1-D18** — configuración y secretos en runtime: perfiles Spring, SSM Parameter Store, KMS managed, convención de nombres, catálogo, App Runner inyecta env vars, lectura sin SDK AWS, log levels via Actuator, rotación, perfil local, IAM mínimos, disparadores de evolución.
- **ADR-0006 D3, D18, D27, D28** — App Runner, Terraform IaC, roles IAM mínimos con OIDC, secretos en SSM.
- **ADR-0011 D5, D13** — `userid-hash-salt` para MDC, activación de DEBUG via Actuator.
- **ADR-0014 D9, D22** — IP truncada + userId hasheado, AWS DPA cubre SSM.
- **ADR-0003 D5, D8, D10, D11, D12** — TTL magic link, sesión, rate limits del módulo Identidad.
- **ADR-0005 D1, D9** — Postmark server token y webhook secret.
- [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md) — guía principal.
- [`testing-de-modulos.md`](testing-de-modulos.md) §2, §4 — `application-test.yml` con valores fake.
- [`rgpd-en-modulos.md`](rgpd-en-modulos.md) §5 — `@AuditaAcceso` aplicable a operaciones críticas como cambio de log level.
