# Tests críticos — módulo Identidad

| Caso | Tipo de test | Por qué duele si falla en producción |
|---|---|---|
| `DeleteUserTest`: el admin no puede eliminarse a sí mismo ni al último admin capaz de entrar al club | Unitario | El club se queda sin ningún admin capaz de entrar; nadie puede gestionar entrenadores, invitaciones ni el resto de operaciones administrativas hasta una intervención manual en BD |
| `DeleteUserTest`: al eliminar un alumno se borran sus datos, se revocan sus sesiones, se audita y se publica la baja | Unitario (RGPD) | Si no se revocan sesiones ni se publica `AlumnoEliminado`, un alumno "borrado" sigue con sesión activa y otros módulos conservan datos personales que debían purgarse |
| `InvitationTest`: una invitación ya consumida no admite un segundo uso | Unitario | Un token de invitación reutilizable permite a un atacante crear una cuenta o tomar el hueco de otra persona con un enlace ya usado o filtrado |
| `MagicLinkTest`: un enlace ya usado se rechaza y un token de propósito distinto (RESETEO) no vale como LOGIN | Unitario | Un magic link de reseteo reutilizado como login, o un enlace reusado, es una vía de secuestro de cuenta sin contraseña |
| `ConsentAuthorizationTest`: un rol sin permiso no puede conceder consentimiento, y no se toca el puerto | Acceso cruzado (ADR-0009 D14) | Un rol no autorizado podría otorgar o revocar consentimientos RGPD en nombre de otro, invalidando la base legal del tratamiento de datos |
| `AuthRateLimitIntegrationTest`: el segundo login fallido dentro de la ventana responde 429 con `Retry-After` | Integración | Sin rate limiting, el login queda expuesto a fuerza bruta de contraseñas sobre cuentas reales |
| `AuthRateLimitIntegrationTest`: pedir dos magic links seguidos al mismo email no reenvía | Integración | Sin este límite, un atacante puede inundar de correos a un alumno o entrenador, o usarlo para enumerar cuentas válidas |
| `AlumnoEliminadoContractTest`: el schema rechaza que el evento lleve datos personales | Contrato | Si el evento de baja filtrase PII (nombre, email) a otros módulos vía el bus de eventos, se rompería la minimización de datos exigida por RGPD en todo el sistema |

Si una PR introduce un caso crítico nuevo, actualiza esta tabla en el mismo commit.
