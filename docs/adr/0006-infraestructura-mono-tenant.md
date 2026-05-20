# ADR-0006 — Infraestructura: mono-tenant en AWS con `club_id` desde el día 1

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance mono-club), `risks.md` (R6 — deuda mono-tenant al generalizar), ADR-0001 (stack), ADR-0004 (base de datos), ADR-0005 (email)

## Contexto y problema

El MVP es **mono-club**: un único club, sus entrenadores y alumnos (`vision.md`). Pero `risks.md` (R6) advierte que asumir "un solo club" en demasiados sitios convierte el paso futuro a multi-club en una reescritura.

Hay que decidir **dónde y cómo se despliega** el sistema: proveedor de nube y forma de la infraestructura. El negocio ha fijado "cloud tradicional (AWS/GCP/Azure)" pero sin elegir cuál.

## Drivers de la decisión

- Alcance MVP **mono-club**: una sola instancia, decenas-cientos de usuarios, carga baja.
- **Equipo por decidir** (ADR-0001) → la nube debe ser *mainstream*, con mucha documentación y pool de contratación amplio.
- Coste bajo en beta.
- El paso futuro a multi-club no debe exigir reescritura (R6).
- Coherencia con el stack JVM (ADR-0001), PostgreSQL gestionado (ADR-0004) y email (ADR-0005).

## Opciones consideradas

### Proveedor de nube

- **AWS** — el más extendido; mayor ecosistema, documentación y pool de contratación. SES (ADR-0005) es nativo.
- **GCP** — buena experiencia de desarrollador; menor cuota de mercado.
- **Azure** — fuerte en entornos corporativos Microsoft; sin ventaja particular aquí.

Con el equipo aún por decidir, **AWS** minimiza el riesgo de contratación y es coherente con la elección de SES. Se elige **AWS**, dejando constancia de que GCP/Azure serían válidas — la decisión no es irreversible si el equipo final tuviera una preferencia fuerte.

### Forma de la infraestructura

- **Opción A** — Servicio gestionado de contenedores/apps + RDS PostgreSQL.
- **Opción B** — Servidor único (una VM/EC2) con todo dentro.
- **Opción C** — Serverless (Lambda + API Gateway).

#### Opción A — App gestionada + RDS

La aplicación Spring Boot empaquetada en contenedor sobre un servicio gestionado de AWS (App Runner o Elastic Beanstalk / ECS); PostgreSQL en Amazon RDS; el build de React servido como estático (S3 + CloudFront) o por la propia app.

- 👍 Poca operación: el proveedor gestiona parcheo, escalado básico y disponibilidad.
- 👍 Escala sin rearquitectura cuando llegue el segundo club.
- 👍 RDS cubre backups y alta disponibilidad de la BD.
- 👎 Algo más caro que una VM única y con más piezas que entender.

#### Opción B — Servidor único (EC2)

Todo —app y BD— en una sola máquina.

- 👍 Lo más barato y simple de arrancar.
- 👎 Punto único de fallo; backups y parcheo manuales; la BD compite por recursos con la app.
- 👎 Crecer obliga a migrar — fricción justo cuando llega tracción.

#### Opción C — Serverless (Lambda)

- 👍 Coste casi cero con tráfico bajo.
- 👎 Spring Boot en Lambda sufre *cold starts*; encaje pobre para una webapp con sesión.
- 👎 Modelo mental distinto — sobrecoste de aprendizaje para un equipo sin definir.

## Decisión

**Nube: AWS. Infraestructura: Opción A — aplicación Spring Boot en contenedor sobre servicio gestionado + Amazon RDS for PostgreSQL.**

Es el equilibrio correcto entre poca operación, coste razonable en beta y capacidad de crecer sin rearquitectura. Evita el punto único de fallo de la VM y el mal encaje de serverless con una webapp con sesión.

Detalles:

- **Aplicación**: Spring Boot empaquetada en contenedor (Docker), desplegada en un servicio gestionado de AWS (App Runner como opción más simple; ECS si se necesita más control — se concreta al implementar).
- **Base de datos**: Amazon RDS for PostgreSQL (ADR-0004), instancia pequeña, con backups automáticos activados.
- **Frontend**: build estático de React servido vía S3 + CloudFront, o por la propia app — se concreta al implementar.
- **Email**: Amazon SES (ADR-0005).
- **Entornos**: al menos `staging` y `producción` separados.
- **IaC**: la infraestructura se define como código (Terraform o AWS CDK) desde el principio — nada de clicar en la consola para recursos permanentes.

**Preparación para multi-club (mitiga R6)** — aunque el MVP sea mono-club:

- `club_id` está presente en **todas** las tablas de dominio desde la primera migración (ya fijado en ADR-0002 y ADR-0004), aunque siempre valga el mismo valor.
- El supuesto "un solo club" se **aísla en pocas capas** (resolución del club actual en auth/scoping), no se esparce por la lógica de negocio.
- No se construye nada de multi-tenant real (enrutado por club, aislamiento) en MVP — solo se evita cerrarse la puerta.

## Consecuencias

### Positivas

- Poca carga de operaciones para un equipo pequeño o por definir.
- Crece a un segundo club sin rearquitectura de infraestructura.
- Nube *mainstream*: contratación y documentación amplias.

### Negativas / coste asumido

- Más caro que una VM única y con más servicios que aprender. Se asume a cambio de fiabilidad y de no migrar al crecer.
- AWS implica cierto *lock-in* (RDS, SES, servicio de apps). Aceptable; el stack en sí (Spring Boot, PostgreSQL, React) es portable.

### Riesgos y mitigaciones

- **Deuda mono-tenant** (R6) → `club_id` en todas las tablas desde el día 1 y supuesto "un club" aislado en pocas capas.
- **Coste que se dispara** → instancias pequeñas en beta; alertas de facturación; revisar dimensionado tras el primer mes con el club piloto.
- **Lock-in de AWS** → IaC versionada y stack base portable; una migración de nube sería trabajo, no reescritura.

## Notas

- La elección AWS vs GCP/Azure puede reabrirse si el equipo final definitivo tuviera experiencia fuerte en otra nube — la arquitectura (contenedor + Postgres gestionado + email) es equivalente en las tres.
- El detalle fino (App Runner vs ECS, dónde se sirve el frontend, CDN) se concreta en la fase de implementación; este ADR fija la forma, no cada recurso.
