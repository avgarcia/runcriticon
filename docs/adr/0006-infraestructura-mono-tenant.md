# ADR-0006 — Infraestructura: mono-tenant en AWS con `club_id` desde el día 1

- **Estado**: Aceptado
- **Fecha**: 2026-05-20 · revisado 2026-05-29 (reorganización Nivel 1: premisas heredadas, NFRs propios, sub-decisiones numeradas D1-D29 con anchors; incorporación de: dimensionado inicial concreto de App Runner y RDS, autoescalado con rangos, política de actualización de PostgreSQL, disparador para Multi-AZ, topología de red con VPC + subnets privadas + VPC connector, acceso administrativo a RDS vía SSM Session Manager sin bastión permanente, dominio propio + estrategia multi-club, CloudFront fuera de MVP con disparador, backend de estado de Terraform en S3 + DynamoDB lock, datos sintéticos en `staging`, convención de tagging obligatoria, alertas de facturación con umbrales, observabilidad sobre CloudWatch en MVP, RTO/RPO objetivo) · **aceptado 2026-05-29**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance mono-club), `risks.md` (R6 — deuda mono-tenant al generalizar), ADR-0001 (stack, cookie first-party), ADR-0003 (Spring Session con almacén compartido al escalar), ADR-0004 (PostgreSQL + esquema por módulo), ADR-0005 (email Postmark, neutral respecto a la nube), ADR-0007 (monolito modular, outbox local en Postgres), ADR-0008 (hexagonal — adaptadores de infraestructura), ADR-0010 (CI/CD, OIDC contra AWS, GHCR), ADR-0013 (secretos en SSM Parameter Store), ADR-0014 (RGPD: residencia UE, cifrado, backups con retención)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre infraestructura. Las veintinueve sub-decisiones se agrupan en diez áreas:

- **Proveedor y forma (D1-D2)** — nube elegida y forma de la infraestructura.
- **Cómputo (D3-D6)** — App Runner, dimensionado y autoescalado, plan de evolución, imagen Docker.
- **Base de datos (D7-D10)** — RDS PostgreSQL, política de actualización, backups, disparador para Multi-AZ.
- **Red y exposición (D11-D14)** — VPC, conector privado a RDS, acceso administrativo, HTTPS.
- **Frontend y dominio (D15-D17)** — estáticos servidos por la app, dominio propio, CloudFront fuera de MVP.
- **IaC y entornos (D18-D21)** — Terraform, backend de estado, entornos, datos en `staging`.
- **Multi-tenant y portabilidad (D22-D23)** — `club_id` desde día 1 y principio de portabilidad entre nubes.
- **Observabilidad y operación (D24-D26)** — logs en CloudWatch, tagging, alertas de facturación.
- **Seguridad (D27-D28)** — IAM mínimos con OIDC y secretos en SSM.
- **Disaster recovery (D29)** — RTO/RPO y plan de restauración.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [Nube: AWS, región `eu-west-1` (Irlanda)](#d1)                                     | Estratégica  |
| D2  | [Forma: app gestionada + RDS](#d2)                                                 | Estratégica  |
| D3  | [AWS App Runner como cómputo](#d3)                                                 | Estratégica  |
| D4  | [Dimensionado App Runner y autoescalado (1 vCPU/2 GB, min 1, max 3)](#d4)          | Operativa    |
| D5  | [ECS Fargate documentado como evolución](#d5)                                      | Operativa    |
| D6  | [Imagen Docker desde GHCR](#d6)                                                    | Operativa    |
| D7  | [RDS PostgreSQL Single-AZ inicial (db.t4g.small, 20 GB)](#d7)                      | Estratégica  |
| D8  | [Política de actualización: auto-minor + major planeado](#d8)                      | Operativa    |
| D9  | [Backups automáticos con retención 30 días](#d9)                                   | Operativa    |
| D10 | [Disparador para Multi-AZ: segundo club o ~500 usuarios activos](#d10)             | Operativa    |
| D11 | [VPC propio con subnets privadas para RDS](#d11)                                   | Estratégica  |
| D12 | [VPC connector App Runner ↔ RDS](#d12)                                             | Operativa    |
| D13 | [Acceso administrativo a RDS: SSM Session Manager + tunneling](#d13)               | Operativa    |
| D14 | [HTTPS gestionado por App Runner + ACM para dominio propio](#d14)                  | Operativa    |
| D15 | [La aplicación sirve los estáticos del frontend](#d15)                             | Estratégica  |
| D16 | [Dominio: `app.runcriticon.com` en MVP; subdominio por club al multi-club](#d16)   | Estratégica  |
| D17 | [CloudFront fuera de MVP con disparador concreto](#d17)                            | Operativa    |
| D18 | [Terraform agnóstico de nube, desde el día 1](#d18)                                | Estratégica  |
| D19 | [Backend de estado de Terraform: S3 + DynamoDB lock](#d19)                         | Operativa    |
| D20 | [Entornos: `staging` y `producción`](#d20)                                         | Estratégica  |
| D21 | [Datos en `staging`: sintéticos generados por scripts](#d21)                       | Estratégica  |
| D22 | [`club_id` desde la primera migración (R6)](#d22)                                  | Estratégica  |
| D23 | [Principio de portabilidad: cloud-specific solo en adaptadores](#d23)              | Estratégica  |
| D24 | [Logs y métricas → CloudWatch en MVP](#d24)                                        | Operativa    |
| D25 | [Convención de tagging obligatoria](#d25)                                          | Operativa    |
| D26 | [Alertas de facturación con umbrales (warning 100 €, crítica 200 €)](#d26)         | Operativa    |
| D27 | [Roles IAM mínimos + OIDC para CI](#d27)                                           | Operativa    |
| D28 | [Secretos en SSM Parameter Store `SecureString`](#d28)                             | Operativa    |
| D29 | [Disaster recovery: RTO < 4 h, RPO < 1 h](#d29)                                    | Estratégica  |

## Contexto y problema

El MVP es **mono-club**: un único club, sus entrenadores y alumnos (`vision.md`). Pero `risks.md` (R6) advierte que asumir "un solo club" en demasiados sitios convierte el paso futuro a multi-club en una reescritura.

Hay que decidir **dónde y cómo se despliega** el sistema: proveedor de nube, forma de la infraestructura y todas las decisiones operativas que el equipo necesita el día 1 para no improvisar (dimensionado, red, IaC, dominio, tagging, alertas, DR).

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Stack Spring Boot + Angular** (ADR-0001 D2, D3). El cómputo aloja un único JAR ejecutable de Spring Boot.
- **Cookie de sesión first-party, mismo dominio que la SPA** (ADR-0001 D11). Condiciona D15 (la app sirve los estáticos).
- **Aplicación login-walled — sin landing pública** (ADR-0001). No hay tráfico anónimo masivo: el dimensionado y el plan CloudFront se calibran sobre usuarios autenticados.
- **PostgreSQL gestionado + esquema por módulo** (ADR-0004 D1, D7). La BD es RDS; un solo motor para toda la app.
- **Spring Modulith + outbox local en Postgres** (ADR-0007 D6). El outbox vive en el mismo RDS; influye en el dimensionado.
- **Postmark como email transaccional, neutral respecto a la nube** (ADR-0005 D1). SES queda fuera; el email no cambia al cambiar de nube.
- **Cifrado en reposo + en tránsito + backups con retención 30 días, no se restauran selectivamente** (ADR-0014 D3, D4, D8). RDS, snapshots y secretos cifrados; backup-restore no resucita PII borrada.
- **Secretos en SSM Parameter Store `SecureString`** (ADR-0013, alineado). KMS encripta cada valor; OIDC desde GitHub Actions accede con rol mínimo.
- **CI/CD GitHub Actions con OIDC contra AWS, imágenes Docker en GHCR** (ADR-0010 D3, D10). No hay claves de larga vida en CI.
- **Dashboard mínimo + alarmas de GitHub Actions** (ADR-0010 D22). La observabilidad runtime se decide en ADR-0011; este ADR sólo fija lo mínimo de plataforma (CloudWatch).
- **Equipo de 4 personas**. Minimizar operación es premisa de coste de tiempo, no sólo de dinero.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Disponibilidad MVP** | ~**99 %** best-effort (ADR-0001) |
| **RTO** (Recovery Time Objective) | **< 4 h** ante pérdida de la app o de la región |
| **RPO** (Recovery Point Objective) | **< 1 h** (límite del último snapshot RDS automático) |
| **Latencia p95 desde Madrid** | **< 200 ms** a `eu-west-1` (Irlanda) |
| **Dimensionado App Runner inicial** | **1 vCPU, 2 GB RAM** por instancia |
| **Autoescalado App Runner** | **min 1, max 3** instancias |
| **Dimensionado RDS inicial** | **db.t4g.small** (2 vCPU, 2 GB) |
| **Tamaño BD inicial** | **20 GB**, autogrow hasta **100 GB** |
| **Coste objetivo MVP** | **< 150 €/mes** en beta |

## Drivers de la decisión

- Alcance MVP **mono-club**: una sola instancia, decenas-cientos de usuarios, carga baja.
- Equipo interno de 4 personas → nube *mainstream*, con mucha documentación.
- Coste bajo en beta.
- El paso futuro a multi-club no debe exigir reescritura (R6).
- **Portabilidad**: poder cambiar de nube sin reescribir la aplicación.
- Coherencia con el stack JVM (ADR-0001) y PostgreSQL gestionado (ADR-0004).
- **El equipo no debe improvisar el día 1**: las decisiones operativas que normalmente se "verán después" (dimensionado, red, IaC, alertas, DR) quedan fijadas aquí.

## Opciones consideradas

### Proveedor de nube

- **AWS** — el más extendido; mayor ecosistema, documentación y pool de contratación.
- **GCP** — buena experiencia de desarrollador; menor cuota de mercado.
- **Azure** — fuerte en entornos corporativos Microsoft; sin ventaja particular aquí.

Se elige **AWS** por ecosistema, documentación y disponibilidad de servicios gestionados maduros. La decisión no es irreversible: GCP y Azure ofrecen servicios equivalentes (ver D23 — principio de portabilidad) y se reabriría si el equipo tuviera una preferencia fuerte.

### Forma de la infraestructura

- **Opción A** — Servicio gestionado de contenedores + RDS PostgreSQL.
- **Opción B** — Servidor único (una VM/EC2) con todo dentro.
- **Opción C** — Serverless (Lambda + API Gateway).

#### Opción A — App gestionada + RDS

- 👍 Poca operación: el proveedor gestiona parcheo, escalado básico y disponibilidad.
- 👍 Escala sin rearquitectura cuando llegue el segundo club.
- 👍 RDS cubre backups y alta disponibilidad de la BD.
- 👎 Algo más caro que una VM única y con más piezas que entender.

#### Opción B — Servidor único (EC2)

- 👍 Lo más barato y simple de arrancar.
- 👎 Punto único de fallo; backups y parcheo manuales; la BD compite por recursos con la app.
- 👎 Crecer obliga a migrar — fricción justo cuando llega tracción.

#### Opción C — Serverless (Lambda)

- 👍 Coste casi cero con tráfico bajo.
- 👎 Spring Boot en Lambda sufre *cold starts*; encaje pobre para una webapp con sesión.
- 👎 Modelo mental distinto — sobrecoste de aprendizaje.

## Decisión

**Nube: AWS. Infraestructura: Opción A — aplicación Spring Boot en contenedor sobre servicio gestionado + Amazon RDS for PostgreSQL.** Las veintinueve sub-decisiones desarrolladas a continuación. Ocho son **estratégicas** (D1, D2, D3, D7, D11, D15, D16, D18, D20, D21, D22, D23, D29 — nube, forma, cómputo elegido, BD, red, frontend, dominio, IaC, entornos, datos en staging, multi-tenant, portabilidad y DR); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Nube: AWS, región `eu-west-1` (Irlanda)

AWS en región **`eu-west-1`**. Cubre la residencia UE/EEE que el RGPD exige (ADR-0014 D1), tiene App Runner disponible (a diferencia de `eu-south-2` Madrid) y es la región más madura.

Toda la infraestructura con datos vive en `eu-west-1`. Recursos globales sin datos personales (IAM, ACM en `us-east-1` si se usa CloudFront) pueden estar fuera; cualquier excepción se documenta.

<a id="d2"></a>
### D2 — Forma: app gestionada + RDS

Aplicación Spring Boot empaquetada en contenedor (Docker) sobre **servicio gestionado de contenedores** (D3) + base de datos **gestionada** (D7). Sin VMs propias, sin operación de hosts.

Evita el punto único de fallo de la VM única y el mal encaje de serverless con una webapp con sesión.

<a id="d3"></a>
### D3 — AWS App Runner como cómputo

La aplicación Spring Boot, empaquetada en contenedor, se despliega en **AWS App Runner** — servicio de contenedores totalmente gestionado. Es el de **menor operación** (balanceo, HTTPS y autoescalado incluidos), lo que encaja con un equipo de 4.

Razones de la elección frente a ECS Fargate:

- Menos piezas que configurar (no hay task definitions, no hay ALB que mantener).
- HTTPS automático con dominio propio vía ACM.
- Despliegue por imagen Docker desde GHCR (D6) sin necesidad de un control plane.
- Coste similar a Fargate para esta escala.

ECS Fargate queda documentado como evolución (D5) si algún límite de App Runner llega a apretar.

<a id="d4"></a>
### D4 — Dimensionado App Runner y autoescalado (1 vCPU/2 GB, min 1, max 3)

- **Instancia**: **1 vCPU, 2 GB RAM** por instancia. Suficiente para Spring Boot (JVM en ~700 MB + holgura) + concurrencia esperada de < 100 req/s en MVP.
- **Autoescalado**: **min 1**, **max 3** instancias.
- **Disparador del escalado**: **concurrencia** (App Runner usa req/instancia, 100 por defecto). 100 req simultáneas por instancia es suficiente para el volumen del piloto.
- **Latencia del escalado**: aceptable la latencia de arranque de Spring Boot (10-20 s) — los NFRs no exigen p95 más estricto y la latencia inicial sólo afecta al primer usuario al escalar.

**Importante**: con `min ≥ 2`, **Spring Session debe migrar a almacén compartido** (ADR-0003 D10 → ElastiCache Redis). En MVP `min=1` por lo que la sesión en memoria es suficiente. **Disparador**: al aumentar `min` a 2 o más, se introduce Redis en la misma PR.

<a id="d5"></a>
### D5 — ECS Fargate documentado como evolución

ECS sobre Fargate queda como evolución si llega alguno de estos disparadores:

- **Necesidad de control de red avanzado** (sidecars, proxies, mTLS interno).
- **Coste sostenido > 200 €/mes** en App Runner con carga continuada (Fargate suele ser más eficiente con uso constante).
- **Límites de App Runner** que aprieten (tamaño de instancia, número de instancias, regiones).

Mientras tanto, App Runner es el cómputo. La migración a Fargate es un cambio de Terraform + cambio del pipeline de despliegue (ADR-0010), no de código de la aplicación.

<a id="d6"></a>
### D6 — Imagen Docker desde GHCR

App Runner consume imágenes Docker desde **GitHub Container Registry (GHCR)** (ADR-0010 D3) por *image-based deployment*: cuando el pipeline de CI publica una nueva versión etiquetada en GHCR, App Runner se redespliega automáticamente (en `staging`) o tras aprobación (en `producción`, ADR-0010 D5/D6).

- Sin Dockerfile en App Runner: la imagen ya viene construida por el pipeline.
- Tag inmutable por commit (ADR-0010 D18); el tag mutable `staging` / `production` apunta a la imagen vigente del entorno.

<a id="d7"></a>
### D7 — RDS PostgreSQL Single-AZ inicial (db.t4g.small, 20 GB)

- **Motor**: PostgreSQL (versión más reciente *mainstream* soportada por RDS al arrancar; típicamente PostgreSQL 16+).
- **Instancia**: **db.t4g.small** (2 vCPU ARM, 2 GB RAM). ARM (Graviton) por mejor relación coste/rendimiento.
- **Almacenamiento**: **20 GB** iniciales, **gp3**, **autogrow hasta 100 GB**.
- **Topología**: **Single-AZ** en MVP. Coherente con la disponibilidad ~99% best-effort.
- **Cifrado en reposo**: activado (cubre snapshots y backups) — ADR-0014 D3.
- **Acceso**: solo desde la app vía VPC connector (D11, D12). RDS **no público**.

El dimensionado incluye margen para:
- Las tablas de los módulos (identidad, club, salud, planificación, auditoría).
- El outbox de Spring Modulith (`event_publication`, ADR-0007 D6) con su pico durante el alta inicial del club.
- Los índices y vacuum del propio Postgres.

<a id="d8"></a>
### D8 — Política de actualización: auto-minor + major planeado

- **Parches minor**: **auto-aplicados** por RDS en la **ventana de mantenimiento** (domingo 04:00-05:00 CET). Cubre seguridad sin trabajo del equipo.
- **Versiones major**: **planeadas por el equipo** con anuncio interno + ventana documentada. Major puede romper compatibilidad (extensiones, deprecated, cambios de comportamiento) y requiere tests previos.
- **Ventana de mantenimiento** declarada en la IaC; cambiarla requiere PR.

<a id="d9"></a>
### D9 — Backups automáticos con retención 30 días

- **Backups automáticos** de RDS activados, **retención 30 días** (alineado con ADR-0014 D8/D10).
- **Snapshots** diarios + Point-in-Time Recovery (PITR) habilitado dentro del periodo de retención.
- **Backups cross-region**: **no en MVP** (coste no justificado a este volumen). Disparador para activar copia cross-region: dependencia funcional del piloto > 99,5 % o el segundo club.
- **No se restauran backups selectivamente** para resucitar PII borrada por derecho de supresión (ADR-0014 D8): el runbook de DR (D29) lo recuerda explícitamente.

<a id="d10"></a>
### D10 — Disparador para Multi-AZ: segundo club o ~500 usuarios activos

RDS Single-AZ → Multi-AZ es un cambio de configuración (`multi_az = true` en Terraform), no rearquitectura. **Disparadores explícitos**:

- Entra el **segundo club** en la plataforma — un fallo afecta a más stakeholders.
- El club piloto alcanza **~500 usuarios activos** sostenidos durante un mes.

Lo que ocurra antes. Coste adicional estimado: ~30 €/mes. Por debajo de los disparadores, Single-AZ es suficiente con la disponibilidad ~99 % best-effort.

<a id="d11"></a>
### D11 — VPC propio con subnets privadas para RDS

Topología de red:

- **VPC propio** en `eu-west-1`, con 3 AZ (para preparar Multi-AZ futuro).
- **Subnets privadas** (sin acceso directo a Internet) para RDS y para los recursos sensibles.
- **Subnets públicas** sólo para NAT Gateway y endpoints externos cuando aplique.
- **NAT Gateway**: una sola para el MVP (1 AZ) — coste ~30 €/mes; aceptable. Se puede pasar a multi-AZ NAT al mismo tiempo que Multi-AZ RDS (D10).

RDS vive **siempre en subnet privada**. No es accesible desde Internet ni con IP pública.

<a id="d12"></a>
### D12 — VPC connector App Runner ↔ RDS

App Runner se conecta a la VPC privada vía **VPC Connector**. Esto permite a la app alcanzar RDS sin exponer la BD a Internet.

- La app se asocia al VPC Connector en su configuración de App Runner.
- Security Group de RDS acepta tráfico **solo desde el security group del VPC connector**.

<a id="d13"></a>
### D13 — Acceso administrativo a RDS: SSM Session Manager + tunneling

Para acceso administrativo puntual (DBA, debugging, hotfix de datos):

- **SSM Session Manager + port forwarding** desde una EC2 efímera o desde el propio terminal del operador.
- **Sin bastión EC2 permanente**: la instancia se levanta sólo cuando se necesita y se destruye al terminar la sesión. Reduce superficie de ataque y coste.
- **Auditoría**: CloudTrail registra cada sesión SSM (quién, cuándo, qué recurso).
- **Acceso IAM-controlado**: solo los roles del equipo con permiso `ssm:StartSession` sobre el target adecuado.

El runbook `docs/runbooks/acceso-rds.md` documenta el procedimiento paso a paso.

<a id="d14"></a>
### D14 — HTTPS gestionado por App Runner + ACM para dominio propio

- **HTTPS**: App Runner termina TLS automáticamente.
- **Dominio propio**: vinculado a App Runner (custom domain).
- **Certificado**: **AWS Certificate Manager (ACM)** emite y renueva automáticamente.
- **Redirección HTTP → HTTPS** activada.
- **HSTS**: cabecera `Strict-Transport-Security` con `max-age` ≥ 6 meses al servir respuestas.

<a id="d15"></a>
### D15 — La aplicación sirve los estáticos del frontend

La **propia aplicación Spring Boot sirve el build estático de Angular**: un solo desplegable y mismo origen — requisito del ADR-0001 D11 (cookie de sesión first-party, mismo dominio).

- El pipeline de CI (ADR-0010) construye Angular y lo empaqueta dentro del JAR de Spring Boot.
- Sirve los estáticos desde el classpath con cache headers conservadores (revisado por entrega).
- **Sin S3 + CloudFront** para el frontend en MVP. La carga de servir HTML/JS/CSS a esta escala es despreciable.
- CloudFront delante de toda la app queda como D17.

<a id="d16"></a>
### D16 — Dominio: `app.runcriticon.com` en MVP; subdominio por club al multi-club

- **MVP mono-club**: la aplicación vive en `app.runcriticon.com`.
- **Al multi-club**: subdominio por club, `{slug-club}.runcriticon.com` (ej. `pikolin.runcriticon.com`). El `club_id` se infiere del subdominio en el adaptador de entrada.
- **Cookie de sesión**: por subdominio (no compartida entre clubes), lo que aísla naturalmente sesiones de distintos clubes.
- **DNS**: gestionado en Route 53.
- **Pendientes técnicos al activar multi-club**: certificado ACM **wildcard** o por subdominio, alias en App Runner por subdominio, lógica de derivación de `club_id` desde host.

<a id="d17"></a>
### D17 — CloudFront fuera de MVP con disparador concreto

CloudFront delante de App Runner **no se activa en MVP**. Disparadores que reabren la decisión:

- **Latencia p95 desde Madrid > 500 ms sostenida** durante 2 semanas.
- **Incidente de DDoS detectado** o ataque sostenido contra el dominio.
- **Coste de salida desde App Runner > 30 €/mes** (CloudFront tiene salida más barata y compensa).

Por debajo, App Runner sirve directamente. La latencia desde Madrid a `eu-west-1` con buena conexión está típicamente < 50 ms — CloudFront en MVP es overhead sin beneficio.

<a id="d18"></a>
### D18 — Terraform agnóstico de nube, desde el día 1

- Toda la infraestructura se define con **Terraform**, versionado en el repo desde la primera línea de código. **Nada de clicar en la consola** para recursos permanentes.
- **Terraform es agnóstico de nube** (no usamos CloudFormation), lo que apoya el principio de portabilidad (D23).
- **Estructura**: módulos por capa lógica (red, cómputo, BD, observabilidad) y composición por entorno.
- **Pull request obligatoria** para cambios en infraestructura (ADR-0010): merge a `main` → `terraform plan` automático → `terraform apply` controlado por el pipeline.

<a id="d19"></a>
### D19 — Backend de estado de Terraform: S3 + DynamoDB lock

El estado de Terraform vive en **S3** (bucket cifrado con KMS, versionado activado) con **DynamoDB** para *state locking*:

- Bucket dedicado `runcriticon-tfstate` con versionado, MFA delete y cifrado en reposo (KMS).
- Tabla DynamoDB `tfstate-lock` (pay-per-request, mínimo coste) para impedir aplicaciones concurrentes.
- Sin Terraform Cloud / HCP Terraform en MVP: el estándar AWS es suficiente y mantiene todo bajo la cuenta del proyecto.
- Backend remoto **obligatorio**: el día 1 nadie aplica desde state local.

<a id="d20"></a>
### D20 — Entornos: `staging` y `producción`

Como mínimo:

- **`staging`** — entorno de pre-producción, espejo de `producción` en topología pero con dimensionado menor permitido y BD con datos sintéticos (D21).
- **`producción`** — entorno servido a los usuarios reales.
- **Aislamiento**: cuentas AWS separadas o VPCs separadas (decisión por implementación; cuentas separadas preferible si AWS Organizations).

Ambos entornos se aprovisionan con el **mismo módulo Terraform** parametrizado, no con copias divergentes. Las diferencias viven en variables, no en código.

<a id="d21"></a>
### D21 — Datos en `staging`: sintéticos generados por scripts

`staging` arranca con **BD vacía** y un script de seed que genera **datos sintéticos** representativos:

- Faker o generadores propios (nombres, emails, marcas plausibles).
- Volumen pequeño pero suficiente para validar flujos (decenas de usuarios por rol).
- El script vive en el repo y se ejecuta con un comando Gradle o CLI.

**Prohibido**: copiar datos de `producción` a `staging` (con o sin anonimización). Razón: la anonimización mal hecha es una filtración (ADR-0014 D6) y el riesgo no compensa para un MVP que puede generar datos sintéticos triviales.

<a id="d22"></a>
### D22 — `club_id` desde la primera migración (R6)

Mitigación explícita del riesgo R6:

- `club_id` está presente en **todas** las tablas de dominio desde la primera migración (ADR-0002, ADR-0004), aunque siempre valga el mismo valor en MVP.
- El supuesto "un solo club" se **aísla en pocas capas** (resolución del club actual en auth/scoping, ADR-0009 D4), no se esparce por la lógica de negocio.
- No se construye nada de multi-tenant real (enrutado por club, aislamiento de red por cliente) en el MVP — solo se evita cerrarse la puerta.

Cuando entre el segundo club (cruce con D10 y D16), el cambio es de propagación del `club_id` desde el host (D16) + endurecimiento de Multi-AZ (D10) — no de modelo de datos.

<a id="d23"></a>
### D23 — Principio de portabilidad: cloud-specific solo en adaptadores

**Objetivo explícito**: poder desplegar en otra nube cambiando **solo el pipeline de despliegue (ADR-0010) y el módulo de Terraform** — nunca el código de la aplicación.

- Nada propietario de una nube se usa en el **dominio** ni en la **capa de aplicación** (ADR-0008); lo *cloud-specific* vive únicamente en **adaptadores de infraestructura**.
- No se usan servicios propietarios de forma gratuita; si hiciera falta uno, va detrás de un puerto.
- Cada servicio *cloud-specific* tiene un equivalente documentado:

| Pieza | AWS | GCP | Azure |
|-------|-----|-----|-------|
| Contenedor gestionado | App Runner | Cloud Run | Container Apps |
| PostgreSQL gestionado | RDS for PostgreSQL | Cloud SQL | Azure Database for PostgreSQL |
| Caché / Redis (futuro) | ElastiCache | Memorystore | Azure Cache for Redis |
| Secretos | SSM Parameter Store | Secret Manager | Key Vault |
| Almacén objetos / TF state | S3 | Cloud Storage | Blob Storage |
| Lock TF state | DynamoDB | GCS object lock | Blob lease |
| Logs y métricas | CloudWatch | Cloud Logging / Monitoring | Azure Monitor |

Una migración de nube es **trabajo acotado pero no trivial**: reescribir el módulo de Terraform, reapuntar configuración, **mover datos** (export/import de BD), **migrar DNS**, **reemitir certificados**, **migrar secretos** y validar adaptadores. Es trabajo de días/semanas, **no de meses** — y, sobre todo, no implica reescribir la aplicación.

<a id="d24"></a>
### D24 — Logs y métricas → CloudWatch en MVP

- **Logs de App Runner**: a CloudWatch Logs por defecto.
- **Logs de RDS**: PostgreSQL slow query log + general log activados, a CloudWatch.
- **Métricas básicas**: CloudWatch Metrics (CPU, memoria, conexiones BD, latencia App Runner).
- **Retención de logs**: 90 días (alineado con ADR-0014 D10).
- **Sin Loki/Grafana/Datadog en MVP**. La evolución hacia observabilidad más sofisticada es decisión de **ADR-0011** (pendiente).

<a id="d25"></a>
### D25 — Convención de tagging obligatoria

Toda recurso AWS aprovisionado por Terraform lleva **obligatoriamente** los siguientes tags:

| Tag | Valor | Uso |
|-----|-------|-----|
| `Project` | `runcriticon` | Identificación del proyecto |
| `Environment` | `staging` \| `production` \| `shared` | Separación de entornos |
| `Module` | `red` \| `computo` \| `bd` \| `obs` \| `seguridad` \| etc. | Atribución de coste por módulo |
| `ManagedBy` | `terraform` | Marca explícita de IaC |
| `CostCenter` | `mvp` \| `piloto` \| etc. | Atribución de coste por iniciativa |

Sin tagging, la factura AWS es ilegible y el control de coste por iniciativa imposible. Los 5 tags son **automáticos por diseño, no verificados por un test**: cada `environments/*/main.tf` declara un `provider "aws"` con alias por módulo (`aws.network`, `aws.database`, …), cada uno con su propio bloque `default_tags` (los 4 comunes + `Module` fijo para ese alias), y cada `module "..." { providers = { aws = aws.<módulo> } }` recibe el suyo — ver `infrastructure/terraform/README.md` §Convención de tagging. Ningún recurso nuevo puede olvidar el tag `Module` porque no lo declara a mano: lo hereda del provider con el que se creó. Esto sustituye al diseño anterior (tag `Module` mezclado a mano por `resource` vía `local.module_tags`, sin nada que verificara que un recurso nuevo lo incluyera).

<a id="d26"></a>
### D26 — Alertas de facturación con umbrales

**AWS Budgets** configurado con dos umbrales:

- **Warning**: 100 €/mes — coincide con el coste objetivo MVP (<150 €). Notificación por email al admin del proyecto.
- **Crítica**: 200 €/mes — el doble del objetivo. Notificación por email al admin + revisión obligatoria del dimensionado.

Frecuencia de revisión: **mensual** (notificación automática + revisión del dashboard de facturación). Sin umbrales, "alerta" se queda en intención.

<a id="d27"></a>
### D27 — Roles IAM mínimos + OIDC para CI

- **Sin claves de larga vida** en CI ni en cuentas de servicio. Coherente con ADR-0010 D10.
- **OIDC desde GitHub Actions**: rol específico `github-actions-runcriticon` con confianza federada en el OIDC provider de GitHub, restringido por `repo:avgarcia/runcriticon:*`.
- **Permisos mínimos** del rol: solo lo necesario para `terraform apply` + `aws apprunner update-service` + acceso a SSM Parameter Store de los secretos relevantes.
- **Operadores humanos**: roles separados (`developer-readonly`, `developer-admin-tfa`) con SSO o IAM Identity Center.
- **Rotación de credenciales humanas**: cada 90 días si fueran necesarias claves IAM (en el MVP se prefiere SSO sin claves).

<a id="d28"></a>
### D28 — Secretos en SSM Parameter Store `SecureString`

- **Secretos** (credenciales BD, API key Postmark, JWT secret, etc.) viven en **AWS Systems Manager Parameter Store** como `SecureString`, cifrados con KMS.
- Coherente con **ADR-0013** (configuración y secretos).
- App Runner consume los secretos en runtime con el rol IAM de la app.
- **Pendiente al activar ADR-0013**: política de rotación y de nombres canónicos.

Frente a AWS Secrets Manager: SSM Parameter Store es suficiente para el MVP (coste menor, sin rotación automática). Si se necesita rotación automática (BD, claves de API rotables), se migra a Secrets Manager — cambio de adaptador, no de modelo.

<a id="d29"></a>
### D29 — Disaster recovery: RTO < 4 h, RPO < 1 h

**Objetivos**:

- **RTO < 4 h**: ante pérdida de la app o de la región, el servicio se restaura en menos de 4 horas. Coherente con disponibilidad ~99 % best-effort.
- **RPO < 1 h**: la pérdida máxima de datos es 1 hora (límite del último snapshot RDS automatizado en su intervalo más fino).

**Plan de restauración** (runbook `docs/runbooks/disaster-recovery.md`):

1. **Reaprovisionamiento de infraestructura** vía Terraform (D18-D19): la IaC en el repo permite levantar todo de cero.
2. **Restauración de RDS** desde el último snapshot disponible (D9).
3. **Reaplicación de la lista de olvidos pendientes** (ADR-0014 D8): la restauración completa puede resucitar PII de usuarios ya borrados; el runbook lo reaplica explícitamente.
4. **Redespliegue de la app** desde la imagen vigente en GHCR (D6).
5. **Validación**: smoke tests del pipeline (ADR-0010) + verificación manual.

**Sin backups cross-region en MVP** (coste no justificado). Si la región completa cae, se acepta una ventana de indisponibilidad mientras AWS la recupera. Disparador para activar cross-region: entrada de un cliente con SLA contractual > 99,5 %.

## Consecuencias

### Positivas

- Las decisiones operativas que normalmente quedan implícitas están **fijadas el día 1**: dimensionado, autoescalado, red, IaC, alertas, DR. El equipo no improvisa.
- Poca carga de operaciones para un equipo pequeño.
- Crece a un segundo club sin rearquitectura de infraestructura; los disparadores para Multi-AZ, ElastiCache y cross-region están explícitos.
- Nube *mainstream*: documentación y servicios maduros.
- Portabilidad disciplinada (D23): cambiar de nube es trabajo acotado, no reescritura.
- Tagging obligatorio (D25) + alertas de facturación (D26) hacen el coste medible y vigilado.
- Datos sintéticos en `staging` (D21) eliminan riesgo RGPD del entorno de pre-producción.
- Secretos cifrados con KMS desde el día 1 (D28); sin claves de larga vida en CI (D27).
- DR documentado con runbook (D29), no sólo como intención.

### Negativas / coste asumido

- Coste inicial mayor que una VM única (~80-120 €/mes vs ~30 €/mes); justificado por fiabilidad y por no migrar al crecer.
- *Lock-in* parcial de AWS (App Runner, RDS, SSM, CloudWatch) — acotado por D23.
- La app sirve los estáticos del frontend (coste de recursos despreciable a esta escala).
- VPC + NAT Gateway añaden ~30 €/mes y complejidad de red — necesarios para que RDS sea privada.
- SSM Session Manager + tunneling tiene curva de aprendizaje mayor que un bastión clásico; compensa por seguridad.
- Multi-AZ, ElastiCache, cross-region y CloudFront quedan como evolución; cada uno con disparador concreto.

### Riesgos y mitigaciones

- **Deuda mono-tenant** (R6) → `club_id` desde el día 1 (D22), supuesto "un club" aislado en pocas capas; cuando llegue el segundo, el cambio es de DNS y propagación (D16), no de modelo.
- **Coste que se dispara** → instancias pequeñas en beta (D4, D7); alertas de facturación con umbrales (D26); revisar dimensionado tras el primer mes con el club piloto.
- **Lock-in de AWS** → Terraform agnóstico (D18), principio de portabilidad (D23) y stack base portable; una migración de nube sería trabajo de días/semanas, no reescritura.
- **RDS expuesto por error** → red privada (D11), security group restringido al VPC connector (D12), test ArchUnit / política IaC que falla el plan si se introduce `publicly_accessible = true`.
- **State de Terraform corrupto o perdido** → backend remoto con versionado (D19), MFA delete activado, copia de seguridad del bucket.
- **Datos sintéticos en `staging` insuficientes para detectar bugs** → script de seed evoluciona con cada funcionalidad nueva; los tests de integración con Testcontainers (ADR-0010) cubren la cobertura técnica.
- **Pérdida de la región completa** → fuera del alcance del MVP (sin cross-region), aceptado conscientemente con disparador para activar.
- **Backup restaurado resucita PII borrada** → runbook DR (D29 paso 3) reaplica la lista de olvidos pendientes (ADR-0014 D8).

## Notas

- Las premisas heredadas son **invariantes de este ADR**: si cambian (ADR-0001, ADR-0014, ADR-0010, ADR-0013), este ADR se revisita.
- La elección AWS vs GCP/Azure puede reabrirse si el equipo definitivo tuviera experiencia fuerte en otra nube — la arquitectura (contenedor + Postgres gestionado + email neutral) es equivalente en las tres (D23).
- **ADR-0011 (Observabilidad)** decidirá si CloudWatch (D24) sigue siendo suficiente o se introduce algo más sofisticado (Loki + Grafana, Datadog…). Este ADR fija el mínimo de plataforma; ADR-0011 lo extiende.
- **ADR-0013 (Configuración y secretos)** detallará la convención de nombres en SSM (D28), la política de rotación y los entornos.
- Servicios previstos para cuando el proyecto crezca, hoy fuera del MVP: **ElastiCache (Redis)** para sesión compartida al activar `min ≥ 2` en App Runner (D4), **RDS Multi-AZ** (D10), **CloudFront** (D17), **backups cross-region** (D9), **ECS/Fargate** (D5), **AWS Secrets Manager** si se necesita rotación (D28).
- **Revisión periódica**: este ADR se revisa al primer mes con el club piloto (ajuste de dimensionado real) y luego cada **6 meses** o cuando un disparador específico se active (D5, D10, D17, etc.).
- **Reorganización del 2026-05-29 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs explícitos, numeración D1-D29 con anchors. Decisiones nuevas o explicitadas: dimensionado concreto de App Runner y RDS (D4, D7), autoescalado con rangos (D4), ECS Fargate con disparadores (D5), política de actualización PostgreSQL (D8), retención backups (D9), disparador Multi-AZ (D10), topología VPC + subnets privadas + connector (D11, D12), acceso a RDS via SSM (D13), HTTPS + ACM (D14), dominio + estrategia multi-club (D16), CloudFront con disparador (D17), backend Terraform en S3 + DynamoDB (D19), datos sintéticos en staging (D21), tagging obligatorio (D25), alertas de facturación con umbrales (D26), OIDC para CI (D27), secretos en SSM SecureString (D28), RTO/RPO con runbook (D29).
- **Revisión del 2026-07-11 (D25)**: D25 afirmaba un "test de Terraform en CI" para el tagging obligatorio que no existía — ningún `*.tftest.hcl` comprobaba tags. En vez de añadir el test, se rediseña el mecanismo para que el tag `Module` (el único que antes se mezclaba a mano por `resource` vía `local.module_tags`, sin verificación) pase a ser automático: cada `environments/*/main.tf` declara un provider AWS con alias por módulo y `default_tags` propio (4 tags comunes + `Module`), y cada `module {}` recibe el suyo. Verificado con Terraform real (`fmt`, `validate`, `test` en los 6 módulos + 2 entornos, igual que CI) — todo en verde. De paso se corrige `infrastructure/terraform/README.md` (afirmaba que el tagging se aplicaba desde `_shared/providers.tf`, un fichero que ningún `environments/*` referencia realmente) y una cita a "ADR-0006 D13" que debía ser D18 (ya corregida en ADR-0006 D18/D27 por otra PR; aquí se corrige la misma cita en el README y en `modules/cicd/main.tf`). Detectado por auditoría de drift documentación-código (23 docs, 61 hallazgos).
