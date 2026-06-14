# Plan de formación — AWS

Objetivo: ser capaces de **desplegar y operar** Runcriticon en AWS con criterio, entendiendo cada servicio que usa la arquitectura (ADR-0006) y por qué.

> Recurso transversal: la **documentación oficial de AWS** y **AWS Skill Builder** (cursos gratuitos de AWS). Practicar siempre en la **capa gratuita (free tier)** con una cuenta de pruebas, nunca en la cuenta de producción.

---

## Nivel 0 — Fundamentos de la nube y de AWS

**Objetivo:** entender el modelo mental de la nube antes de tocar servicios.

- Qué es la nube: modelo de responsabilidad compartida, *pago por uso*.
- **Regiones y zonas de disponibilidad (AZ)** — qué son y por qué importan para la disponibilidad.
- La **cuenta de AWS**: organización, usuarios, facturación, alertas de coste, capa gratuita.
- **IAM** a nivel introductorio: usuarios, roles, políticas, principio de **mínimo privilegio**.

**Conexión Runcriticon:** ADR-0006 elige una región; ADR-0001 fija una disponibilidad *best-effort* ~99% en una sola AZ para la beta. Entender AZ explica esa decisión.

---

## Nivel 1 — Los servicios que usa Runcriticon

**Objetivo:** conocer a fondo los servicios concretos del proyecto.

- **AWS App Runner** — despliegue de contenedores gestionado. Cómo publicar una imagen, autoescalado, HTTPS, variables de entorno. *(Es el servicio elegido para la app — ADR-0006.)*
- **Amazon RDS for PostgreSQL** — base de datos gestionada: instancias, backups automáticos, parámetros, conexión. *(ADR-0004.)*
- **Amazon ECR** — registro de imágenes de contenedor (de dónde tira App Runner).
- **Amazon S3 + CloudFront** — almacenamiento de estáticos y CDN. *(Candidato para servir el frontend Angular — pendiente en ADR-0006.)*
- **AWS Secrets Manager / Parameter Store** — dónde viven las credenciales y la configuración sensible.

**Conexión Runcriticon:** este nivel es el "mapa" de la infraestructura del ADR-0006. Al terminarlo se debería poder leer ese ADR y reconocer cada pieza.

---

## Nivel 2 — Red y seguridad

**Objetivo:** desplegar de forma segura, no solo funcional.

- **VPC** básico: subredes, enrutado, qué es estar "en privado".
- **Security groups** — el cortafuegos de cada recurso.
- **IAM a fondo** — roles para servicios, políticas afinadas, evitar credenciales de larga vida.
- **Cifrado** en reposo (RDS, S3) y en tránsito (TLS/HTTPS).
- Gestión de **secretos**: nada de credenciales en el código ni en el repositorio.

**Conexión Runcriticon:** la base de datos no debe ser accesible desde internet; la app habla con RDS por red privada. Conecta con el plan de **Seguridad Web/API**.

---

## Nivel 3 — Operación y observabilidad

**Objetivo:** saber si el sistema está sano y reaccionar cuando no lo está.

- **Amazon CloudWatch** — logs, métricas, *dashboards*, alarmas.
- **Alarmas de facturación** — avisos de coste antes de un susto.
- **Backups y restauración** de RDS — y probar que la restauración funciona.
- Estrategia de **entornos**: `staging` y `producción` separados (ADR-0006).

**Conexión Runcriticon:** ADR-0006 pide alarmas de coste y revisar el dimensionado tras el primer mes con el club piloto.

---

## Nivel 4 — Infraestructura como código y evolución

**Objetivo:** que la infraestructura sea reproducible y preparar el crecimiento.

- **Infraestructura como código (IaC)** — Terraform o AWS CDK (decisión del ADR-0006): definir todos los recursos en ficheros versionados.
- Servicios para **cuando el proyecto crezca**:
  - **Amazon ElastiCache (Redis)** — caché y sesión compartida al escalar a varias instancias (ver ADR-0003 y la nota de tipos de base de datos).
  - **RDS Multi-AZ** — alta disponibilidad real de la base de datos.
  - **ECS sobre Fargate** — alternativa a App Runner si hace falta más control (ADR-0006).

**Conexión Runcriticon:** estos servicios **no** están en el MVP a propósito; este nivel explica hacia dónde puede evolucionar la infraestructura y por qué se aplazaron.

---

## Práctica recomendada

Desplegar, en una cuenta de capa gratuita, un contenedor sencillo en App Runner conectado a una instancia pequeña de RDS PostgreSQL, definido con IaC. Es el "ensayo general" de la arquitectura del ADR-0006.

## Recursos de partida

- Documentación oficial de AWS (por servicio) y **AWS Skill Builder**.
- Certificación de referencia para fundamentos: *AWS Certified Cloud Practitioner*; para el equipo técnico: *AWS Certified Solutions Architect – Associate*.
- Guías de **Terraform** (HashiCorp) o **AWS CDK**, según lo que decida el ADR-0006.
