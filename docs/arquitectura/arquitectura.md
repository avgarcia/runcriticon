# Arquitectura de Runcriticon — diagramas

| Campo | Valor |
|---|---|
| Alcance | Vistas C4 (contexto, contenedores, componentes), dos secuencias clave y despliegue AWS |
| Estado | Refleja lo **implementado en `main`** (hito H0); el despliegue refleja el entorno `staging` de Terraform |
| Fecha | 2026-09-22 |
| Versión | 1.0 |
| Autor | avgarcia (con Claude Code) |
| Fuente de verdad | Los 17 ADRs de [`docs/adr/`](../adr/). Si un diagrama contradice un ADR, **gana el ADR** y el diagrama se corrige |

Todos los diagramas son Mermaid `flowchart`/`sequenceDiagram` y comparten notación:

| Elemento | Significado |
|---|---|
| Rectángulo azul | Sistema o contenedor **interno** de Runcriticon |
| Rectángulo gris | Sistema **externo** (SaaS o servicio de terceros) |
| Rectángulo verde redondeado | Persona usuaria (actor) |
| Cilindro | Almacén de datos |
| Rectángulo con borde discontinuo | Decidido en un ADR pero **aún no implementado ni provisionado** |
| Flecha sólida `-->` | Llamada **síncrona** (petición/respuesta); apunta al que recibe la petición |
| Flecha discontinua `-.->` | Flujo **asíncrono** (evento, email); apunta al consumidor |
| Recuadro (subgraph) | Límite: sistema, proceso, red o cuenta |

Siglas: **SPA** (Single Page Application), **C4** (Context-Container-Component-Code), **RDS** (Relational Database Service), **SSM** (AWS Systems Manager Parameter Store), **KMS** (Key Management Service), **OIDC** (OpenID Connect), **GHCR** (GitHub Container Registry), **ECR** (Elastic Container Registry), **VPC** (Virtual Private Cloud), **NAT** (Network Address Translation), **AMP/AMG** (Amazon Managed Prometheus / Grafana), **RBAC** (Role-Based Access Control), **RGPD** (Reglamento General de Protección de Datos).

---

## 1. C4 nivel 1 — Contexto del sistema

**Público**: producto y negocio. **Pregunta**: ¿quién usa Runcriticon y con qué sistemas externos habla?

```mermaid
flowchart LR
    admin(["Administrador del club<br/>rol ADMIN"])
    entrenador(["Entrenador<br/>rol ENTRENADOR"])
    alumno(["Alumno<br/>rol ALUMNO"])

    rc["Runcriticon<br/>Planificación y seguimiento<br/>de entrenamientos de un club"]

    postmark["Postmark<br/>Email transaccional"]
    aws["AWS eu-west-1<br/>Hosting, BD, secretos, logs"]

    admin -->|"Invita entrenadores y alumnos, gestiona el club (HTTPS)"| rc
    entrenador -->|"Organiza grupos, publica planes, revisa reportes (HTTPS)"| rc
    alumno -->|"Consulta su plan, reporta sesiones y marcas (HTTPS)"| rc
    rc -->|"Envía invitaciones y magic links (HTTPS API)"| postmark
    postmark -.->|"Entrega el email"| entrenador
    postmark -.->|"Entrega el email"| alumno
    rc -->|"Se ejecuta sobre"| aws

    classDef person fill:#dcfce7,stroke:#15803d,color:#14532d
    classDef internal fill:#dbeafe,stroke:#1d4ed8,color:#1e3a8a
    classDef external fill:#e5e7eb,stroke:#4b5563,color:#111827
    class admin,entrenador,alumno person
    class rc internal
    class postmark,aws external
```

Notas:

- **Mono-club** en el MVP ([`vision.md`](../vision.md)); `club_id` ya viaja en datos y sesión desde el día 1 (ADR-0006, ADR-0009).
- **Rol único por usuario** en el MVP (ADR-0003); multi-rol aplazado (ADR-0015).
- Acceso **solo por invitación** (ADR-0003): no hay registro abierto.
- El admin también recibe email (reset de contraseña); se omite la flecha para no recargar.

---

## 2. C4 nivel 2 — Contenedores

**Público**: arquitectura y liderazgo técnico. **Pregunta**: ¿qué piezas desplegables hay y cómo se comunican?

```mermaid
flowchart LR
    user(["Usuario<br/>ADMIN / ENTRENADOR / ALUMNO"])

    subgraph rc["Runcriticon (un único proceso, mismo origen)"]
        spa["SPA<br/>Angular 22 + spartan.ng + Tailwind v4<br/>estáticos servidos por Spring"]
        api["Monolito modular<br/>Kotlin + Spring Boot 4 + Spring Modulith 2<br/>GraalVM CE 25 JIT"]
    end

    db[("PostgreSQL 16<br/>un esquema por módulo<br/>+ event_publication (outbox)<br/>+ Spring Session JDBC")]
    ssm["Parámetros SSM<br/>SecureString"]
    postmark["Postmark<br/>API de email"]
    logs["CloudWatch Logs"]

    user -->|"Carga la SPA (HTTPS)"| spa
    spa -->|"REST/JSON en /api, cookie de sesión httpOnly (HTTPS)"| api
    api -->|"Lee y escribe entidades, outbox y sesión (JDBC)"| db
    ssm -.->|"App Runner los inyecta como variables de entorno al arrancar"| api
    api -.->|"Envía emails tras el commit (HTTPS API)"| postmark
    api -.->|"Emite logs JSON con MDC (stdout)"| logs

    classDef person fill:#dcfce7,stroke:#15803d,color:#14532d
    classDef internal fill:#dbeafe,stroke:#1d4ed8,color:#1e3a8a
    classDef external fill:#e5e7eb,stroke:#4b5563,color:#111827
    class user person
    class spa,api,db internal
    class ssm,postmark,logs external
```

Notas:

- **Mismo origen**: la SPA y `/api` los sirve la misma aplicación Spring Boot; sin CORS, sin SSR, sin GraphQL (ADR-0001). El frontend **nunca** lee tokens desde JS (ADR-0003).
- El cliente HTTP del frontend se **genera desde OpenAPI** (ADR-0001 D10, ADR-0012 D12).
- La aplicación **no usa el SDK de AWS**: App Runner inyecta los parámetros SSM como variables de entorno y Spring los lee de `Environment` (ADR-0013).
- La sesión es **Spring Session JDBC** desde el día 1 (ADR-0006 D4 revisada).
- Se envía email por Postmark (ADR-0005). No hay endpoint de webhook entrante de Postmark implementado aún, aunque el secreto ya existe en SSM.

---

## 3. C4 nivel 3 — Componentes: módulos y eventos

**Público**: desarrollo. **Pregunta**: ¿qué *bounded contexts* hay dentro del monolito y qué eventos intercambian?

Ninguna llamada síncrona cruza módulos (ADR-0007): todo va por *integration events* publicados en el outbox de Spring Modulith (`event_publication`), y cada consumidor mantiene una **proyección local**. Las flechas agrupan eventos por familia; la tabla siguiente es la referencia completa.

```mermaid
flowchart LR
    subgraph app["Monolito modular — com.runcriticon"]
        identidad["identidad<br/>usuarios, invitaciones,<br/>sesión, consentimiento"]
        club["clubtaxonomia<br/>grupos, tags, membresías"]
        plan["planificacion<br/>planes semanales,<br/>personalizaciones"]
        seg["seguimiento<br/>reportes, marcas,<br/>reajustes de día"]
        audit["auditoria<br/>traza de accesos"]
        shared["shared<br/>autorizacion, rgpd.AuditAccessAspect,<br/>events, observability"]
    end

    identidad -.->|"Alumno/Entrenador Invitado y Activado"| club
    identidad -.->|"Consentimiento Concedido/Revocado"| seg
    identidad -.->|"Borrado: Alumno/Entrenador/Admin Eliminado"| club
    identidad -.->|"Borrado: Alumno/Entrenador Eliminado"| plan
    identidad -.->|"Borrado: Alumno/Entrenador Eliminado"| seg
    identidad -.->|"Borrado: anonimiza la traza"| audit
    club -.->|"EntrenadorAsignado/EliminadoDeGrupo, MembresiaDeGrupoCambiada"| plan
    club -.->|"EntrenadorAsignado/EliminadoDeGrupo"| seg
    plan -.->|"PlanPublicado, Personalizacion Aplicada/Retirada"| seg
    seg -.->|"LesionDeclarada"| club
    identidad -.->|"AccesoDenegado"| audit
    club -.->|"AccesoDenegado"| audit
    plan -.->|"AccesoDenegado"| audit
    shared -.->|"AccesoADatosSensibles"| audit
    identidad -->|"Consulta AuthorizationMatrix (en proceso)"| shared

    classDef internal fill:#dbeafe,stroke:#1d4ed8,color:#1e3a8a
    classDef core fill:#ede9fe,stroke:#6d28d9,color:#3b0764
    class identidad,club,plan,seg,audit internal
    class shared core
```

Leyenda adicional: el recuadro violeta es el **núcleo compartido** (`com.runcriticon.shared`), usado en proceso por todos los módulos; solo se dibuja una de esas dependencias síncronas para no saturar.

### Tabla de integration events (fuente: `api/events/` y `@ApplicationModuleListener` en `main`)

| Evento | Productor | Consumidores (listener) |
|---|---|---|
| `AlumnoInvitado`, `AlumnoActivado`, `EntrenadorInvitado`, `EntrenadorActivado` | identidad | clubtaxonomia (`PersonProjectionListener`) |
| `AlumnoEliminado`, `EntrenadorEliminado` | identidad | clubtaxonomia (`StudentDeletionListener`), planificacion (`PlanificacionDeletionListener`), seguimiento (`SeguimientoDeletionListener`), auditoria (`AuditTrailAnonymizationListener`) |
| `AdminEliminado` | identidad | clubtaxonomia (`StudentDeletionListener`), auditoria (`AuditTrailAnonymizationListener`) |
| `ConsentimientoConcedido`, `ConsentimientoRevocado` | identidad | seguimiento (`ConsentProjectionListener`) |
| `EntrenadorAsignadoAGrupo`, `EntrenadorEliminadoDeGrupo` | clubtaxonomia | planificacion (`GroupMembersProjectionListener`), seguimiento (`CoachGroupProjectionListener`) |
| `MembresiaDeGrupoCambiada` | clubtaxonomia | planificacion (`GroupMembersProjectionListener`), clubtaxonomia (`MergeSuggestionListener`, autoconsumo) |
| `PlanPublicado` | planificacion | seguimiento (`ResolvedPlanProjectionListener`) |
| `PersonalizacionAplicada`, `PersonalizacionRetirada` | planificacion | seguimiento (`PersonalizationProjectionListener`) |
| `MarcaActualizada`, `MarcaRetirada` | seguimiento | seguimiento (`MarkPaceRecalculationListener`, autoconsumo) |
| `ReporteRegistrado`, `DiaReajustado` | seguimiento | — (sin consumidor todavía) |
| `LesionDeclarada` (`shared.api.events`) | seguimiento (`RescheduleDayCommand`) | clubtaxonomia (`LesionDeclaradaListener`) |
| `AccesoDenegado` (`shared.api.events`) | identidad, clubtaxonomia, planificacion (`*AccessAuditor` y casos de uso) | auditoria (`AuditEventListener`) |
| `AccesoADatosSensibles` (`shared.api.events`) | `shared.rgpd.AuditAccessAspect` (`@AuditaAcceso`) | auditoria (`AuditEventListener`) |

Los eventos internos de identidad (`InvitationEmailRequested`, `MagicLinkEmailRequested`, `PasswordResetEmailRequested`) **no** son integration events: no salen del módulo y solo disparan el envío de email.

---

## 4. Secuencia — Inicio de sesión por magic link

**Público**: desarrollo y seguridad. **Pregunta**: ¿cómo se autentica un usuario sin contraseña? (ADR-0003, ADR-0005)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant SPA as SPA Angular
    participant MLC as MagicLinkController<br/>(identidad)
    participant DB as PostgreSQL<br/>(identidad + outbox)
    participant L as MagicLinkEmailListener
    participant PM as Postmark
    participant SSM as SecuritySessionManager

    U->>SPA: Introduce su email
    SPA->>MLC: POST /api/sesion/magic-link {email}
    MLC->>DB: Guarda token (TTL 15 min) y MagicLinkEmailRequested en la misma transacción
    MLC-->>SPA: 202 Accepted (neutro: no revela si la cuenta existe)
    DB-->>L: Entrega el evento tras el commit (@ApplicationModuleListener)
    L->>PM: Envía email con el enlace (HTTPS API)
    PM-->>U: Email con magic link
    U->>SPA: Abre el enlace
    SPA->>MLC: POST /api/sesion/magic-link/consumo {token}
    MLC->>DB: Valida y consume el token (un solo uso)
    MLC->>SSM: startSession(principal)
    SSM->>DB: Crea la sesión (Spring Session JDBC)
    MLC-->>SPA: 200 {userId, clubId, role} + Set-Cookie httpOnly, SameSite=Lax, Secure
```

Notas: los dos endpoints son `@NoAuthRequired`. Un fallo de Postmark no revierte la solicitud: el envío ocurre en la transacción del listener, posterior al commit del caso de uso.

---

## 5. Secuencia — Publicación de un plan y proyección en seguimiento

**Público**: desarrollo. **Pregunta**: ¿cómo viaja un cambio entre módulos sin llamada síncrona? (ADR-0007, ADR-0009 D9)

```mermaid
sequenceDiagram
    autonumber
    actor E as Entrenador
    participant API as PlanController<br/>(planificacion)
    participant CMD as PublishPlanCommand
    participant DBP as PostgreSQL<br/>planificacion + event_publication
    participant RL as ResolvedPlanProjectionListener<br/>(seguimiento)
    participant DBS as PostgreSQL<br/>seguimiento

    E->>API: Publica el plan (REST/JSON, cookie de sesión)
    API->>CMD: execute(...)
    CMD->>CMD: Consulta AuthorizationMatrix (RBAC + nivel de objeto)
    alt Acceso denegado
        CMD->>DBP: Publica AccesoDenegado en el outbox
        CMD-->>API: Left(Forbidden)
        API-->>E: 403 {code, message}
    else Autorizado
        CMD->>DBP: Guarda el plan y PlanPublicado en la misma transacción
        CMD-->>API: Right(plan)
        API-->>E: 200
        DBP-->>RL: Entrega PlanPublicado tras el commit
        RL->>DBS: markIfNew(listener, eventId) en evento_procesado
        alt Evento ya procesado
            RL->>RL: Descarta (idempotencia)
        else Nuevo
            RL->>DBS: Actualiza la proyección del plan resuelto y last_processed_event_id/ts
        end
    end
```

Notas: el listener restaura el MDC (`MdcRestorerForEvents`) al empezar y lo limpia en `finally`. Si la proyección acumula más de 60 s de retraso, las lecturas que dependen de ella fallan cerradas con `ProjectionStale` (ADR-0009 D9). Un evento que falla queda sin completar en `event_publication`; no hay reintento con *backoff* (ADR-0007).

---

## 6. Despliegue — AWS `eu-west-1` (entorno `staging`)

**Público**: operación y arquitectura. **Pregunta**: ¿dónde se ejecuta cada contenedor? Fuente: [`infrastructure/terraform/`](../../infrastructure/terraform/) (módulos `network`, `runtime`, `database`, `secrets`, `observability`, `cicd`).

```mermaid
flowchart LR
    user(["Usuario"])
    gha["GitHub Actions<br/>pipeline de despliegue, no implementado"]
    ghcr["GHCR<br/>imagen por commit, no implementado"]
    postmark["Postmark"]

    subgraph aws["Cuenta AWS — eu-west-1"]
        oidc["Rol IAM de despliegue<br/>asumido vía OIDC"]
        ecr["ECR<br/>tags inmutables"]
        apprunner["App Runner<br/>monolito + SPA, autoscaling"]
        ssm["SSM SecureString<br/>cifrado con KMS"]
        cw["CloudWatch Logs"]
        budget["AWS Budgets<br/>presupuesto mensual"]
        subgraph vpc["VPC"]
            nat["NAT Gateway<br/>subred pública"]
            conn["VPC connector<br/>subred privada"]
            rds[("RDS PostgreSQL 16<br/>subred privada, cifrado KMS")]
        end
        amp["AMP + AMG + X-Ray<br/>ADR-0011, no provisionado"]
    end

    user -->|"HTTPS, dominio propio"| apprunner
    gha -.->|"Publica la imagen (previsto)"| ghcr
    gha -.->|"Asume el rol, OIDC sin claves (previsto)"| oidc
    oidc -.->|"Replica la imagen de GHCR a ECR (previsto)"| ecr
    oidc -.->|"UpdateService al nuevo tag (previsto)"| apprunner
    apprunner -->|"Descarga la imagen"| ecr
    apprunner -->|"Inyecta secretos como env vars"| ssm
    apprunner -->|"Tráfico saliente por"| conn
    conn -->|"JDBC, security group connector → database"| rds
    conn -->|"Salida a Internet"| nat
    nat -->|"HTTPS API"| postmark
    apprunner -.->|"Logs de aplicación"| cw
    apprunner -.->|"Métricas y trazas (previsto)"| amp
    budget -.->|"Vigila el gasto de"| apprunner

    classDef person fill:#dcfce7,stroke:#15803d,color:#14532d
    classDef internal fill:#dbeafe,stroke:#1d4ed8,color:#1e3a8a
    classDef external fill:#e5e7eb,stroke:#4b5563,color:#111827
    classDef planned fill:#f9fafb,stroke:#6b7280,stroke-dasharray:5 5,color:#374151
    class user person
    class apprunner,rds internal
    class postmark,oidc,ecr,ssm,cw,budget,nat,conn external
    class amp,gha,ghcr planned
```

Notas:

- **Pipeline de despliegue aún no implementado**: en `.github/workflows/` solo existen `ci.yml` y `adr-site.yml`. Sí están en Terraform el proveedor OIDC, el rol de despliegue y el ECR. Diseño previsto (ADR-0010 D2): GHCR como registro primario y ECR como réplica por entorno, de la que tira App Runner. Rollback = redesplegar un tag anterior (ADR-0010 D12).
- RDS PostgreSQL 16 en **Single-AZ** (`multi_az = false` por defecto); Multi-AZ aplazado (ADR-0015).
- Hoy solo existe el entorno `staging` (más `localstack` para desarrollo); producción queda por provisionar.
- AMP/AMG/X-Ray están **decididos** (ADR-0011) pero no hay recursos Terraform todavía. Por eso, como el pipeline, llevan borde discontinuo.
