# Configuración — módulo `auditoria`

Catálogo de secretos y propiedades no secretas que consume este módulo, según [`configuracion-y-secretos-en-modulos.md`](../../../../../../../docs/arquitectura/configuracion-y-secretos-en-modulos.md) §3. Fuente de verdad verificada contra `backend/src/main/resources/application.yml`.

## Secretos consumidos

Ninguno. El módulo no tiene `@ConfigurationProperties` de secretos ni lee ningún valor SSM propio.

## Propiedades no secretas

| Propiedad | Valor por defecto | Uso |
|---|---|---|
| `runcriticon.auditoria.retention.cron` | `0 30 3 * * *` | Cron del job de retención (`AuditoriaRetentionJob`) que purga `evento` a los 24 meses y `evento_procesado` a los 30 días. Desfasado 30 min de `club_taxonomia` para no competir por conexión a la misma hora (ver comentario en `application.yml`) |

El `LIMIT` de la consulta forense (`AuditEventRepositoryImpl.search`) y los plazos de retención son constantes en
código, no propiedades.

## Solo local (`application-local.yml`, nunca en staging/producción)

Ninguna propiedad específica de este módulo.
