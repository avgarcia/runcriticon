# Configuración — módulo `auditoria`

Sin propiedades de configuración propias ni secretos (`@ConfigurationProperties`) — ningún módulo del repo
tiene todavía este tipo de configuración (el `LIMIT` de la consulta forense y el umbral de retención de
`RGPD.md` son constantes en código, no propiedades). Se añadirá aquí si en el futuro hace falta (ej. el
`LIMIT` de `AuditEventRepositoryImpl.search` como propiedad, o el cron del job de purga pendiente).
