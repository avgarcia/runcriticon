# Configuración — módulo Club y taxonomía

Catálogo de secretos y propiedades no secretas que consume este módulo, según [`configuracion-y-secretos-en-modulos.md`](../../../../../../../docs/arquitectura/configuracion-y-secretos-en-modulos.md) §3. Fuente de verdad verificada contra `backend/src/main/resources/application.yml`.

## Secretos consumidos

Ninguno. El módulo no tiene `@ConfigurationProperties` de secretos ni lee ningún valor SSM propio.

## Propiedades no secretas

| Propiedad | Valor por defecto | Uso |
|---|---|---|
| `runcriticon.club-taxonomia.retention.cron` | `0 0 3 * * *` | Cron del job de retención (`ClubTaxonomiaRetentionJob`, LAL-107) que purga `persona_eliminada` y `evento_procesado`. Desfasado 15 min de `auditoria` para no competir por conexión a la misma hora (ver comentario en `application.yml`) |

## Solo local (`application-local.yml`, nunca en staging/producción)

Ninguna propiedad específica de este módulo.
