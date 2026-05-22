# ADR-0013 — Configuración y secretos en *runtime*

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0004 (credenciales de BD), ADR-0005 (clave de Postmark), ADR-0006 (infraestructura, App Runner, portabilidad), ADR-0010 (CI/CD — secretos del pipeline)

## Contexto y problema

La aplicación en ejecución necesita **configuración que varía por entorno** (`staging` vs `producción`: conexión a la BD, parámetros) y **secretos** (contraseña de la base de datos, clave de API de Postmark, clave de firma de sesión). ADR-0010 cubre los secretos del *pipeline*; ADR-0006 cubre la IaC. Falta decidir **cómo recibe la aplicación, ya desplegada, su configuración y sus secretos**.

## Drivers de la decisión

- **Nada de secretos en el repositorio ni en el código** (coherente con ADR-0010).
- Configuración distinta por entorno, sin recompilar.
- **Portabilidad** (ADR-0006): la aplicación no debe acoplarse al mecanismo de una nube concreta.
- Coste contenido; equipo de 4 → solución simple.

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

> También se consideró usar *Kubernetes Secrets*. Se descarta: la arquitectura no incluye Kubernetes (ADR-0006 decidió App Runner), y los *Kubernetes Secrets* no son un gestor de secretos completo. La portabilidad que aportarían ya la da el diseño de abajo, sin necesidad de un clúster.

## Decisión

### Configuración no secreta

Va en **perfiles de Spring** (`application-staging.yml`, `application-production.yml`) y en **variables de entorno**. Cada entorno (ADR-0006) activa su perfil.

### Secretos — Opción A: SSM Parameter Store

Los secretos se guardan en **AWS SSM Parameter Store** como `SecureString`. Es barato, simple y suficiente para los pocos secretos del MVP. **Secrets Manager** (con rotación automática) queda documentado como mejora posterior, y solo para la contraseña de la BD, si se quiere rotación.

### Cómo llegan los secretos a la aplicación

- El **servicio de despliegue (App Runner)** referencia los parámetros de Parameter Store y los **inyecta como variables de entorno** en el contenedor.
- La aplicación los lee a través de la abstracción **`Environment` de Spring** (`@ConfigurationProperties`) — **no usa ningún SDK de AWS**.
- Consecuencia: la aplicación **no conoce la nube**. El día que la plataforma de despliegue cambie (otra nube, u otro mecanismo), inyectará las variables a su manera y la aplicación **no cambia** — coherente con el principio de portabilidad de ADR-0006.

### Higiene

- **Ningún secreto** en el repositorio ni en el código; el escaneo de secretos del pipeline (ADR-0010) lo vigila.
- En **desarrollo local**, un perfil `local` con valores de pruebas; nunca secretos reales de `staging`/`producción`.

## Consecuencias

### Positivas

- Configuración por entorno sin recompilar.
- Secretos fuera del código y del repositorio.
- La aplicación es portable: solo ve variables de entorno, no el mecanismo de la nube.
- Coste mínimo (Parameter Store estándar es gratuito).

### Negativas / coste asumido

- Sin rotación automática de secretos en el MVP — se asume; Secrets Manager queda como evolución.
- Las variables de entorno con secretos viven en el proceso; mitigado porque App Runner las inyecta desde Parameter Store, no van en texto plano en la configuración del despliegue.

### Riesgos y mitigaciones

- **Un secreto se cuela en el repositorio** → escaneo de secretos en CI (ADR-0010); revisión de código.
- **Secreto comprometido sin rotación** → rotación manual documentada; adoptar Secrets Manager para la contraseña de la BD si el riesgo lo justifica.

## Notas

- **Secrets Manager** (rotación automática) es la evolución natural para la contraseña de la BD cuando se quiera; el cambio no toca la aplicación, solo de dónde App Runner lee el secreto.
- Los secretos del *pipeline* de CI/CD (distintos de los de *runtime*) se gestionan en GitHub con OIDC — ver ADR-0010.
