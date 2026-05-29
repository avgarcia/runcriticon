# ADR-0013 — Configuración y secretos en *runtime*

- **Estado**: Aceptado
- **Fecha**: 2026-05-22 · revisado 2026-05-29 (reorganización Nivel 1: premisas heredadas, NFRs propios, sub-decisiones numeradas D1-D18 con anchors; incorporación de: **convención de nombres canónica en SSM**, **catálogo nominal de secretos del MVP**, **política de rotación detallada por tipo**, **aclaración del mecanismo "log levels sin redespliegue"** cruzado con ADR-0011 D13, KMS managed key, acceso humano via CLI con auditoría CloudTrail, roles IAM mínimos por path SSM, perfil local concretado con docker-compose + MailHog, disparadores para evolución a Secrets Manager / CMK / Vault) · **aceptado 2026-05-29**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (Spring Boot, perfiles), ADR-0004 (credenciales de BD), ADR-0005 (clave de Postmark, webhook secret), ADR-0006 (App Runner, SSM Parameter Store, OIDC, IAM mínimos, datos sintéticos en staging), ADR-0010 (CI/CD — escaneo de secretos, OIDC), ADR-0011 (log levels activables, MDC con salt rotado), ADR-0014 (RGPD: cifrado en reposo, subencargados con DPA)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre configuración y secretos en runtime. Las dieciocho sub-decisiones se agrupan en ocho áreas:

- **Configuración no secreta (D1-D2)** — perfiles Spring y selección por entorno.
- **Almacén de secretos (D3-D5)** — SSM `SecureString`, cifrado KMS, convención de nombres.
- **Catálogo (D6)** — lista nominal de los secretos del MVP.
- **Inyección y lectura (D7-D8)** — App Runner inyecta env vars; la app no conoce la nube.
- **Cambios en runtime (D9)** — log levels via Actuator; resto por redeploy.
- **Rotación (D10-D11)** — política por tipo y procedimiento manual.
- **Higiene y acceso (D12-D15)** — sin secretos en repo, perfil local, acceso humano auditado, roles IAM mínimos.
- **Evolución (D16-D18)** — disparadores para Secrets Manager / CMK / Vault.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [Configuración en perfiles Spring + variables de entorno](#d1)                     | Estratégica  |
| D2  | [Selección de perfil por `SPRING_PROFILES_ACTIVE`](#d2)                            | Operativa    |
| D3  | [SSM Parameter Store `SecureString` como almacén de secretos](#d3)                 | Estratégica  |
| D4  | [Cifrado con KMS managed `aws/ssm`](#d4)                                           | Operativa    |
| D5  | [Convención de nombres `/runcriticon/{env}/{component}/{name}`](#d5)               | Estratégica  |
| D6  | [Catálogo nominal de los secretos del MVP](#d6)                                    | Estratégica  |
| D7  | [App Runner inyecta secretos como variables de entorno](#d7)                       | Operativa    |
| D8  | [La aplicación lee via `Environment` Spring sin SDK AWS](#d8)                      | Estratégica  |
| D9  | [Log levels via `/actuator/loggers`; resto por redeploy](#d9)                      | Operativa    |
| D10 | [Política de rotación: trimestral DB, anual crypto y proveedor](#d10)              | Estratégica  |
| D11 | [Procedimiento de rotación manual documentado en runbook](#d11)                    | Operativa    |
| D12 | [Sin secretos en repo: escaneo en CI (cruce ADR-0010)](#d12)                       | Operativa    |
| D13 | [Perfil local: docker-compose + MailHog + valores fake](#d13)                      | Operativa    |
| D14 | [Acceso humano via AWS CLI + auditoría CloudTrail](#d14)                           | Operativa    |
| D15 | [Roles IAM mínimos por path SSM (cruce ADR-0006 D27)](#d15)                        | Operativa    |
| D16 | [Disparador → Secrets Manager para rotación automática](#d16)                      | Operativa    |
| D17 | [Disparador → CMK (KMS customer-managed)](#d17)                                    | Operativa    |
| D18 | [Disparador → Vault / Doppler para multi-cloud](#d18)                              | Operativa    |

## Contexto y problema

La aplicación en ejecución necesita **configuración que varía por entorno** (`staging` vs `producción`: conexión a la BD, parámetros) y **secretos** (contraseña de la base de datos, clave de API de Postmark, claves criptográficas). ADR-0010 cubre los secretos del *pipeline*; ADR-0006 cubre la IaC y fija SSM Parameter Store como sede (D28). Falta decidir **cómo recibe la aplicación, ya desplegada, su configuración y sus secretos** — y, sobre todo, las cinco decisiones operativas que sin convención común el equipo improvisa: nombres en SSM, catálogo de secretos, política de rotación, mecanismo de cambio en runtime, y acceso humano.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Stack Spring Boot** (ADR-0001 D2) — `@ConfigurationProperties` y perfiles son la API nativa.
- **Credenciales de PostgreSQL gestionado** (ADR-0004 D1) — uno de los secretos del catálogo.
- **Postmark API key + webhook secret** (ADR-0005, ADR-0005 D9) — secretos del catálogo.
- **AWS `eu-west-1` + App Runner** (ADR-0006 D1/D3) — plataforma de inyección.
- **SSM Parameter Store `SecureString` como sede de secretos** (ADR-0006 D28) — premisa central; este ADR la concreta.
- **OIDC desde GitHub Actions para CI** (ADR-0010 D10, ADR-0006 D27) — la app accede via rol IAM de App Runner, **no** desde CI.
- **Escaneo de secretos en CI** (ADR-0010) — gatekeeper anti-leak.
- **Activación de DEBUG/TRACE en incidente sin redespliegue** (ADR-0011 D13) — este ADR aclara el mecanismo (D9).
- **`userId` hash salt rotado anualmente** (ADR-0011 D5, ADR-0014 D9) — uno de los secretos del catálogo.
- **Cifrado en reposo con KMS** (ADR-0014 D3) — los secretos en SSM se cifran con KMS.
- **AWS GDPR DPA cubre SSM** (ADR-0014 D22) — sin DPA adicional para el almacén de secretos.
- **Equipo de 4 personas** — premisa de coste de tiempo.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Tiempo desde rotación de secreto → app en uso (p95)** | **< 10 min** (redeploy App Runner) |
| **Frecuencia de rotación DB password** | **trimestral** en MVP (manual, en calendario) |
| **Frecuencia de rotación claves criptográficas** (signing, salt) | **anual** + ante sospecha |
| **Frecuencia de rotación tokens de proveedor** (Postmark) | **anual** + ante sospecha |
| **Acceso a secretos por humanos** | **siempre** via AWS CLI / SSM con rol IAM federado SSO; **nunca** por consola con copia, **nunca** por chat |
| **Coste objetivo de SSM** | **0 €/mes** (parámetros estándar gratuitos) |
| **Cobertura del catálogo** | **100 %** — todo secreto vivo está en el catálogo (D6); no hay secretos sueltos |

## Drivers de la decisión

- **Nada de secretos en el repositorio ni en el código** (coherente con ADR-0010).
- Configuración distinta por entorno, sin recompilar.
- **Portabilidad** (ADR-0006): la aplicación no debe acoplarse al mecanismo de una nube concreta.
- Coste contenido; equipo de 4 → solución simple.
- **El equipo no debe improvisar el día 1**: convención de nombres, catálogo, rotación y mecanismo de runtime quedan fijados aquí.

## Opciones consideradas — almacén de secretos

- **Opción A** — AWS SSM Parameter Store (`SecureString`).
- **Opción B** — AWS Secrets Manager.

### Opción A — SSM Parameter Store

- 👍 **Barato** — los parámetros estándar no tienen coste.
- 👍 Simple; suficiente para el puñado de secretos del MVP; App Runner los referencia e inyecta de forma nativa.
- 👎 Sin rotación automática de secretos integrada.

### Opción B — AWS Secrets Manager

- 👍 **Rotación automática** de secretos integrada (útil sobre todo para la contraseña de la BD).
- 👎 Coste por secreto y mes; más de lo que un MVP con pocos secretos necesita.

> También se consideró usar *Kubernetes Secrets*. Se descarta: la arquitectura no incluye Kubernetes (ADR-0006 decidió App Runner), y los *Kubernetes Secrets* no son un gestor de secretos completo.

## Decisión

**Opción A: SSM Parameter Store `SecureString`** para secretos + **perfiles Spring + variables de entorno** para configuración no secreta. Las dieciocho sub-decisiones desarrolladas a continuación. Seis son **estratégicas** (D1, D3, D5, D6, D8, D10 — configuración, almacén, convención de nombres, catálogo, lectura neutral, política de rotación); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Configuración en perfiles Spring + variables de entorno

- Configuración **no secreta** vive en `application-{profile}.yml` (`staging`, `production`, `local`).
- Cada perfil declara lo que cambia entre entornos: dimensionado del pool de conexiones, niveles de log por defecto, URLs públicas, *feature flags*.
- Las variables de entorno **inyectadas por App Runner** sobreescriben los valores del perfil cuando aplica.
- Sin perfiles propietarios escondidos (`@Profile("aws")`): la abstracción es Spring estándar.

<a id="d2"></a>
### D2 — Selección de perfil por `SPRING_PROFILES_ACTIVE`

- App Runner inyecta `SPRING_PROFILES_ACTIVE=staging` o `=production` según el entorno (parámetro de servicio).
- Localmente, `application-local.yml` se activa con `SPRING_PROFILES_ACTIVE=local` (D13).
- **Un solo perfil activo** por proceso: combinar perfiles ya en MVP introduce ambigüedad innecesaria.

<a id="d3"></a>
### D3 — SSM Parameter Store `SecureString` como almacén de secretos

- Premisa heredada de ADR-0006 D28.
- Los **parámetros estándar** son gratuitos hasta 10 000 / cuenta — más que suficiente para el catálogo del MVP (D6).
- App Runner los **referencia e inyecta como variables de entorno** (D7); la app no llama al SDK AWS (D8).

<a id="d4"></a>
### D4 — Cifrado con KMS managed `aws/ssm`

- `SecureString` cifra con KMS. En MVP se usa la **AWS managed key `aws/ssm`** (gratuita, automática, sin coste extra).
- AWS rota internamente la key managed; sin intervención del equipo.
- **Disparador para Customer Managed Key (CMK)**: ver D17.

<a id="d5"></a>
### D5 — Convención de nombres `/runcriticon/{env}/{component}/{name}`

Convención canónica para todos los secretos en SSM:

```
/runcriticon/{env}/{component}/{name}
```

| Parte | Valores |
|-------|---------|
| `{env}` | `staging` \| `production` |
| `{component}` | `db` \| `email` \| `crypto` \| `auditoria` \| ... (kebab-case) |
| `{name}` | nombre canónico del secreto (kebab-case) |

Ejemplos:
- `/runcriticon/production/db/password`
- `/runcriticon/production/email/postmark-server-token`
- `/runcriticon/production/email/postmark-webhook-secret`
- `/runcriticon/production/crypto/session-signing-key`
- `/runcriticon/production/crypto/userid-hash-salt`
- `/runcriticon/production/crypto/magic-link-signing-key`

**Razones**:

- SSM permite **filtros y políticas IAM por path prefix** — roles mínimos por componente (D15).
- La jerarquía hace los listados legibles (`aws ssm get-parameters-by-path --path /runcriticon/production/crypto/`).
- La convención fija el naming desde el primer secreto: cualquier desviación es PR.

Cualquier secreto fuera de esta convención no entra en producción.

<a id="d6"></a>
### D6 — Catálogo nominal de los secretos del MVP

Lista completa de los secretos del runtime en el MVP. La cobertura es **100 %** (NFR): todo secreto vivo está aquí o no existe.

| Nombre canónico | Componente | Origen | Rotación | Cruce |
|---|---|---|---|---|
| `db/password` | RDS | Generado al provisionar RDS | **Trimestral** manual (D10) | ADR-0004 D1 |
| `email/postmark-server-token` | Postmark | Postmark dashboard | **Anual** + sospecha | ADR-0005 D1 |
| `email/postmark-webhook-secret` | Postmark | Generado por nosotros | **Anual** + sospecha | ADR-0005 D9 |
| `crypto/session-signing-key` | Spring Session | Generado por nosotros (256 bits aleatorios) | **Anual** | ADR-0003 D10 |
| `crypto/userid-hash-salt` | Observabilidad / logs | Generado por nosotros (256 bits aleatorios) | **Anual** | ADR-0011 D5, ADR-0014 D9 |
| `crypto/magic-link-signing-key` | Identidad | Generado por nosotros (256 bits aleatorios) | **Anual** | ADR-0003 D5/D8 |

**Generación de claves criptográficas**: 256 bits de entropía, generadas con `openssl rand -hex 32` o equivalente. **Nunca generadas en máquina compartida**; siempre en una sesión efímera y se introducen directamente en SSM via CLI.

Añadir un secreto nuevo requiere PR que actualice este catálogo + el RAT (ADR-0014 D19). Sin esta sincronización, el escaneo CI del catálogo (cuando se implemente) detectará la desviación.

<a id="d7"></a>
### D7 — App Runner inyecta secretos como variables de entorno

- En la IaC de App Runner (Terraform, ADR-0006 D18), cada secreto se declara como **referencia a un path SSM**.
- App Runner los lee al **arranque del contenedor** y los expone como variables de entorno con un nombre estándar (uppercase, snake-case): `DB_PASSWORD`, `POSTMARK_SERVER_TOKEN`, `SESSION_SIGNING_KEY`, etc.
- El rol IAM de la tarea de App Runner tiene permiso `ssm:GetParameter` y `kms:Decrypt` solo sobre el path `/runcriticon/{env}/*`.

<a id="d8"></a>
### D8 — La aplicación lee via `Environment` Spring sin SDK AWS

- La app declara `@ConfigurationProperties` o `@Value("${db.password}")` apuntando a las env vars inyectadas.
- **No usa ningún SDK de AWS** para leer secretos en runtime. Tampoco usa Spring Cloud AWS Parameter Store. La abstracción es `Environment` de Spring, estándar.
- Consecuencia: el día que la plataforma cambie (otra nube, otro mecanismo de inyección), la app **no cambia** — el nuevo proveedor inyectará las env vars a su manera. Portabilidad real (cruce ADR-0006 D23).

<a id="d9"></a>
### D9 — Log levels via `/actuator/loggers`; resto por redeploy

**Aclaración del cruce con ADR-0011 D13** *("DEBUG/TRACE activable por configuración sin redespliegue")*: el mecanismo concreto es **Spring Boot Actuator** `/actuator/loggers`, **no** un refresh dinámico de SSM.

- **Log levels**: cambio en runtime via `POST /actuator/loggers/{logger}` con `{"configuredLevel": "DEBUG"}`. Endpoint protegido con auth (rol `ADMIN`) y solo accesible desde el VPC (cruce ADR-0006 D11). **Sin redeploy**.
- **Resto de configuración** (URLs, dimensionado de pool, *feature flags*): cambio en SSM o en perfil → **redeploy de App Runner** (~5-10 min, NFR). Es lo bastante raro como para no justificar `Spring Cloud AWS Parameter Store con @RefreshScope`, que añadiría dependencia y superficie sin valor proporcional al volumen del piloto.

La consecuencia operativa: la promesa de ADR-0011 D13 *"sin redespliegue"* aplica solo a log levels. Otros cambios de config son redeploy. No se inventan caminos intermedios.

<a id="d10"></a>
### D10 — Política de rotación: trimestral DB, anual crypto y proveedor

| Tipo | Cadencia | Disparadores adicionales |
|------|----------|--------------------------|
| **DB password** (`db/password`) | **Trimestral** (calendario equipo) | Salida de personal con acceso, sospecha de compromiso |
| **Claves criptográficas** (`crypto/*`) | **Anual** | Sospecha de compromiso |
| **Tokens de proveedor** (`email/postmark-*`) | **Anual** | Sospecha de compromiso, alerta del proveedor |

- La cadencia trimestral / anual está pensada para un MVP con equipo de 4: **más estricto degradaría la cultura** (rotaciones que no se hacen pierden todo el valor).
- **Rotación inmediata** ante sospecha real es invariante, no negociable.
- Cuando el equipo crezca o entre una auditoría externa, el disparador de D16 lleva a Secrets Manager con rotación automática.

<a id="d11"></a>
### D11 — Procedimiento de rotación manual documentado en runbook

Runbook `docs/runbooks/rotacion-secretos.md` con el procedimiento paso a paso:

1. **Generar el nuevo valor** (`openssl rand -hex 32` para crypto; nuevo Postmark token desde su dashboard; `aws rds modify-db-instance` para BD).
2. **Persistir en SSM** via `aws ssm put-parameter --name /runcriticon/{env}/{component}/{name} --value "..." --type SecureString --overwrite`.
3. **Redeploy de App Runner** para que las env vars se actualicen (~5-10 min).
4. **Verificación** del flujo afectado (login para session signing, magic link para magic-link signing, envío de email para Postmark, conexión BD para DB password).
5. **Revocar el valor antiguo** cuando el proveedor lo permita (no aplica a crypto/* — la rotación es nueva, no se conserva el viejo).
6. **Audit log** en `docs/runbooks/log-rotaciones.md`: fecha, secreto, ejecutor.

Sin runbook el procedimiento se improvisa en la primera rotación urgente — momento donde nadie quiere improvisar.

<a id="d12"></a>
### D12 — Sin secretos en repo: escaneo en CI (cruce ADR-0010)

- **Ningún secreto** en el repositorio ni en el código.
- El **escaneo de secretos en CI** (ADR-0010) bloquea PRs con detección de patrones de secretos (AWS keys, Postmark tokens, `BEGIN PRIVATE KEY`, etc.).
- Pre-commit hook opcional para los desarrolladores; el bloqueo definitivo es en CI.
- Si un secreto se filtra accidentalmente al historial: rotación inmediata + revisión + reescritura de historia (si aplica) + postmortem.

<a id="d13"></a>
### D13 — Perfil local: docker-compose + MailHog + valores fake

`application-local.yml` para desarrollo en máquina:

- **Postgres local** via `docker-compose.yml` en el repo (alineado con datos sintéticos de ADR-0006 D21).
- **MailHog** levantado por docker-compose para email (cruce ADR-0005 D14).
- **Postmark sandbox token** provisto al desarrollador fuera de banda (al onboarding), nunca commiteado.
- **Crypto keys fake** con prefijo `local-dev-only-not-for-prod-` para que sean visiblemente no productivas en cualquier log.
- **Sin acceso a SSM real** desde local: ningún rol IAM federado de desarrollador puede leer `/runcriticon/staging/*` ni `/runcriticon/production/*` por defecto.

**Razón del bloqueo a SSM staging desde local**: lo cómodo (leer secretos de staging) rompe la barrera entre entornos. PII de staging acabaría replicada en máquinas locales, no auditada, sin protección. Si la diferencia es dolorosa para el desarrollo, se ajustan los valores fake del perfil local — no se abre el acceso.

<a id="d14"></a>
### D14 — Acceso humano via AWS CLI + auditoría CloudTrail

Cuando un humano necesita acceder a un secreto (incidente, debugging, rotación):

- **AWS CLI con rol IAM federado por SSO** (IAM Identity Center, ADR-0006 D27).
- `aws ssm get-parameter --name /runcriticon/production/db/password --with-decryption`.
- **CloudTrail registra cada acceso** (`GetParameter` con `withDecryption=true` es registrado y disparable en alarma de AMG D15).
- **Nunca**: copiar al portapapeles del SO sin necesidad, pegar en chat, escribir en log, enviar por email.

Política operativa documentada en `docs/runbooks/acceso-secretos.md` (parte del onboarding).

<a id="d15"></a>
### D15 — Roles IAM mínimos por path SSM (cruce ADR-0006 D27)

Permisos IAM por rol siguiendo el principio de mínimo privilegio:

| Rol | Permisos SSM |
|-----|--------------|
| **App Runner task role** (servicio) | `ssm:GetParameter` + `kms:Decrypt` sobre `/runcriticon/{env}/*` solo del entorno correspondiente |
| **Developer read-only** (SSO) | `ssm:GetParameter` sobre `/runcriticon/staging/*` solo (sin producción) |
| **Developer admin** (SSO, con MFA) | `ssm:GetParameter` + `ssm:PutParameter` sobre `/runcriticon/staging/*` y `/runcriticon/production/*` |
| **CI / GitHub Actions OIDC** | **Sin acceso a secretos de runtime**. Solo Terraform aplica cambios en SSM via su propio rol con permisos limitados |

Los **secretos del CI/CD** (distintos de los de runtime) viven en GitHub Actions secrets, no en SSM (cruce ADR-0010).

<a id="d16"></a>
### D16 — Disparador → Secrets Manager para rotación automática

**Migración a AWS Secrets Manager** (para algunos o todos los secretos) cuando:

- **Equipo > 4 personas**: la rotación manual se olvida con más probabilidad.
- **Incidente con secretos**: tras un compromiso real, la rotación automática deja de ser opcional.
- **Cliente con regulación específica** que exige rotación frecuente y automatizada.

Cuando se active, se migra **DB password primero** (Secrets Manager tiene integración nativa con RDS para rotación). Crypto y proveedor pueden quedarse en SSM si las rotaciones manuales anuales se siguen ejecutando.

<a id="d17"></a>
### D17 — Disparador → CMK (KMS customer-managed)

**Migración de `aws/ssm` (managed) a Customer Managed Key (CMK)** cuando:

- **Auditoría externa** (RGPD, ISO, SOC) que pida control sobre la rotación de la propia key.
- **Cliente con regulación específica**.
- **Necesidad de política propia** (grant a roles específicos, deny a otros).

Coste CMK: ~1 €/mes + 0.03$/10K usos. Despreciable para el MVP pero injustificado sin disparador concreto.

<a id="d18"></a>
### D18 — Disparador → Vault / Doppler para multi-cloud

**Migración a HashiCorp Vault, Doppler o equivalente** cuando:

- **Multi-cloud real**: el producto se despliega en AWS + GCP / Azure y necesita un gestor de secretos común.
- **> 50 secretos**: la convención de nombres en SSM empieza a fricciónar y un gestor con jerarquía rica aporta valor.
- **Necesidades avanzadas**: secretos dinámicos (cortos), brokering de identidad, audit logs centralizados.

Mientras tanto, SSM Parameter Store + la convención de D5 es suficiente para el MVP mono-cloud.

## Consecuencias

### Positivas

- Configuración por entorno sin recompilar.
- Secretos fuera del código y del repositorio.
- **La aplicación es portable**: solo ve variables de entorno, no el mecanismo de la nube (D8).
- **Convención de nombres** (D5) y **catálogo** (D6) fijados el día 1: el equipo no improvisa.
- **Política de rotación** (D10) concreta con cadencia: las rotaciones no se postergan indefinidamente.
- **Coste mínimo** (Parameter Store estándar es gratuito).
- **Acceso humano auditado** (D14 + CloudTrail): responsabilidad proactiva (RGPD cubierto).
- **Roles IAM mínimos por path** (D15): un fallo en un componente no compromete los secretos de otro.
- **Aclaración del cruce con ADR-0011 D13** (D9): el equipo sabe exactamente qué se cambia sin redespliegue (log levels via Actuator) y qué requiere redeploy.
- **Disparadores explícitos** (D16-D18) para evolución a Secrets Manager / CMK / Vault.

### Negativas / coste asumido

- **Sin rotación automática en MVP** — se asume; Secrets Manager queda como evolución con disparador (D16).
- Las variables de entorno con secretos viven en el proceso; mitigado porque App Runner las inyecta desde Parameter Store, no van en texto plano en la configuración del despliegue.
- **Acceso developer admin a producción** existe (D15) — auditado pero accesible. Mitigación: MFA obligatoria, alarma sobre `PutParameter` en `production/*` en horario inusual.
- **Mecanismo de log levels distinto al del resto de config** (D9): el equipo debe recordar que un cambio "sin redespliegue" es Actuator, y un cambio de URL pública es redeploy.

### Riesgos y mitigaciones

- **Un secreto se cuela en el repositorio** → escaneo de secretos en CI (D12) + revisión de código + rotación inmediata + reescritura del historial si aplica.
- **Secreto comprometido sin rotación detectado tarde** → política de rotación con cadencia (D10) + CloudTrail con alarma sobre accesos inusuales.
- **Rotación manual olvidada** → calendario del equipo con recordatorios + audit log de rotaciones (D11) revisable.
- **Naming inconsistente que rompe IAM** → convención canónica (D5) + revisión PR de toda IaC que cree parámetros nuevos.
- **Catálogo desincronizado** → cobertura 100 % como NFR + PR obligatoria para añadir un secreto + escaneo CI cuando se implemente.
- **Acceso developer admin a producción mal usado** → MFA + audit + revisión periódica de roles asignados.
- **PII de staging filtrada a local** → bloqueo explícito de acceso a SSM staging desde perfil local (D13).

## Notas

- Las premisas heredadas (especialmente ADR-0006 D28, ADR-0010, ADR-0011 D13, ADR-0014 D3/D9) son **invariantes de este ADR**: si cambian, este ADR se revisita.
- **Secrets Manager** (rotación automática) es la evolución natural para DB password cuando se cumpla el disparador de D16.
- Los **secretos del pipeline** de CI/CD (distintos de los de runtime) se gestionan en **GitHub Actions secrets** con OIDC contra AWS — ver ADR-0010 D10. CI **no accede** a los secretos de runtime de SSM (D15).
- **Cookies, cabeceras, datos en sesión** se cifran/firman con `crypto/session-signing-key` (D6). La rotación anual implica que las sesiones activas se invalidan al rotar — coherente con ADR-0003 D11 (revocación inmediata).
- **Revisión periódica**: este ADR se revisa a los **6 meses** del lanzamiento o cuando un disparador de D16-D18 se active.
- **Reorganización del 2026-05-29 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs explícitos, numeración D1-D18 con anchors. Decisiones nuevas explicitadas: convención de nombres `/runcriticon/{env}/{component}/{name}` (D5), catálogo nominal de 6 secretos del MVP (D6), política de rotación trimestral DB / anual crypto y proveedor (D10), procedimiento de rotación manual en runbook (D11), aclaración del mecanismo "log levels sin redespliegue" cruzado con ADR-0011 D13 (D9), KMS managed `aws/ssm` (D4), perfil local docker-compose + MailHog + fakes (D13), acceso humano via CLI + CloudTrail (D14), roles IAM mínimos por path SSM (D15), disparadores para Secrets Manager / CMK / Vault (D16-D18).
