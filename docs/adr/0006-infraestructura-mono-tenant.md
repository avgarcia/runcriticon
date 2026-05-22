# ADR-0006 — Infraestructura: mono-tenant en AWS con `club_id` desde el día 1

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance mono-club), `risks.md` (R6 — deuda mono-tenant al generalizar), ADR-0001 (stack), ADR-0004 (base de datos), ADR-0005 (email), ADR-0008 (hexagonal — adaptadores de infraestructura), ADR-0010 (CI/CD)

## Contexto y problema

El MVP es **mono-club**: un único club, sus entrenadores y alumnos (`vision.md`). Pero `risks.md` (R6) advierte que asumir "un solo club" en demasiados sitios convierte el paso futuro a multi-club en una reescritura.

Hay que decidir **dónde y cómo se despliega** el sistema: proveedor de nube y forma de la infraestructura. El negocio ha fijado "cloud tradicional (AWS/GCP/Azure)" pero sin elegir cuál.

## Drivers de la decisión

- Alcance MVP **mono-club**: una sola instancia, decenas-cientos de usuarios, carga baja.
- Equipo interno de 4 personas (ADR-0001) → nube *mainstream*, con mucha documentación.
- Coste bajo en beta.
- El paso futuro a multi-club no debe exigir reescritura (R6).
- **Portabilidad**: poder cambiar de nube sin reescribir la aplicación.
- Coherencia con el stack JVM (ADR-0001) y PostgreSQL gestionado (ADR-0004).

## Opciones consideradas

### Proveedor de nube

- **AWS** — el más extendido; mayor ecosistema, documentación y pool de contratación.
- **GCP** — buena experiencia de desarrollador; menor cuota de mercado.
- **Azure** — fuerte en entornos corporativos Microsoft; sin ventaja particular aquí.

Se elige **AWS** por ecosistema, documentación y disponibilidad de servicios gestionados maduros. La decisión no es irreversible: GCP y Azure ofrecen servicios equivalentes (ver *principio de portabilidad*) y se reabriría si el equipo tuviera una preferencia fuerte.

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

**Nube: AWS. Infraestructura: Opción A — aplicación Spring Boot en contenedor sobre servicio gestionado + Amazon RDS for PostgreSQL.**

Es el equilibrio correcto entre poca operación, coste razonable en beta y capacidad de crecer sin rearquitectura. Evita el punto único de fallo de la VM y el mal encaje de serverless con una webapp con sesión.

### Cómputo — AWS App Runner

La aplicación Spring Boot, empaquetada en contenedor (Docker), se despliega en **AWS App Runner** — servicio de contenedores totalmente gestionado. Es el de **menor operación** (balanceo, HTTPS y autoescalado incluidos), lo que encaja con un equipo de 4. **ECS sobre Fargate** queda documentado como evolución si algún límite de App Runner (control de red, coste con carga sostenida) llega a apretar.

### Base de datos — Amazon RDS for PostgreSQL

Instancia pequeña, con backups automáticos activados. **Single-AZ** para el MVP — coherente con la disponibilidad *best-effort* ~99% del ADR-0001. Pasar a **Multi-AZ** (alta disponibilidad real) es un cambio de configuración, no de arquitectura, y queda para cuando el negocio lo justifique.

### Frontend

La **propia aplicación Spring Boot sirve el *build* estático de Angular**: un solo desplegable y mismo origen — requisito del ADR-0001 (cookie de sesión *first-party*, mismo dominio). No se usa S3 + CloudFront para el frontend en el MVP; poner CloudFront delante de toda la app es un añadido posible más adelante.

### Email

Amazon SES queda descartado: el email transaccional usa **Postmark** (ADR-0005), que además es neutral respecto a la nube.

### Infraestructura como código — Terraform

Toda la infraestructura se define con **Terraform**, versionado, desde el principio — nada de clicar en la consola para recursos permanentes. Terraform es agnóstico de nube, lo que apoya el principio de portabilidad.

### Entornos

Al menos `staging` y `producción` separados.

### Principio de portabilidad

**Objetivo explícito**: poder desplegar en otra nube cambiando **solo el pipeline de despliegue y el módulo de Terraform** — nunca el código de la aplicación.

- Nada propietario de una nube se usa en el **dominio** ni en la **capa de aplicación**; lo *cloud-specific* vive únicamente en **adaptadores de infraestructura** (ADR-0008).
- No se usan servicios propietarios de forma gratuita; si hiciera falta uno, va detrás de un puerto.
- Cada servicio *cloud-specific* tiene un equivalente documentado:

| Pieza | AWS | GCP | Azure |
|-------|-----|-----|-------|
| Contenedor gestionado | App Runner | Cloud Run | Container Apps |
| PostgreSQL gestionado | RDS for PostgreSQL | Cloud SQL | Azure Database for PostgreSQL |
| Caché / Redis (futuro) | ElastiCache | Memorystore | Azure Cache for Redis |

Así una migración de nube es **trabajo acotado** (reescribir el módulo de Terraform y reapuntar configuración), **no una reescritura**.

### Preparación para multi-club (mitiga R6)

Aunque el MVP sea mono-club:

- `club_id` está presente en **todas** las tablas de dominio desde la primera migración (ADR-0002, ADR-0004), aunque siempre valga el mismo valor.
- El supuesto "un solo club" se **aísla en pocas capas** (resolución del club actual en auth/scoping), no se esparce por la lógica de negocio.
- No se construye nada de multi-tenant real (enrutado por club, aislamiento) en el MVP — solo se evita cerrarse la puerta.

## Consecuencias

### Positivas

- Poca carga de operaciones para un equipo pequeño.
- Crece a un segundo club sin rearquitectura de infraestructura.
- Nube *mainstream*: documentación y servicios maduros.
- Portabilidad disciplinada: cambiar de nube es trabajo acotado, no reescritura.

### Negativas / coste asumido

- Más caro que una VM única y con más servicios que aprender. Se asume a cambio de fiabilidad y de no migrar al crecer.
- *Lock-in* parcial de AWS (App Runner, RDS) — acotado, con equivalentes documentados en GCP/Azure.
- La app sirve los estáticos del frontend (coste de recursos despreciable a esta escala).

### Riesgos y mitigaciones

- **Deuda mono-tenant** (R6) → `club_id` en todas las tablas desde el día 1 y supuesto "un club" aislado en pocas capas.
- **Coste que se dispara** → instancias pequeñas en beta; alertas de facturación; revisar dimensionado tras el primer mes con el club piloto.
- **Lock-in de AWS** → Terraform agnóstico, principio de portabilidad y stack base portable; una migración de nube sería trabajo, no reescritura.

## Notas

- La elección AWS vs GCP/Azure puede reabrirse si el equipo definitivo tuviera experiencia fuerte en otra nube — la arquitectura (contenedor + Postgres gestionado + email neutral) es equivalente en las tres.
- El pipeline de **compilación, calidad de código y despliegue** se decide en un ADR aparte.
- Servicios previstos para cuando el proyecto crezca, hoy fuera del MVP: **ElastiCache (Redis)** para sesión compartida y caché al escalar a varias instancias (ADR-0003), **RDS Multi-AZ** y **ECS/Fargate**.
