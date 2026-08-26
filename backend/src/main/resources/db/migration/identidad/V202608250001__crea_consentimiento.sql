-- Consentimiento explícito de datos de salud, Art. 9.2.a RGPD (ADR-0014 D16/D18, LAL-128). Base legal
-- del tratamiento que captura seguimiento.reporte_sesion (LAL-30): sin esto, no hay base legal.
--
-- Categoría RGPD: PII_PRIMARIA (categoría 1). Retención: igual que identidad.usuario (baja + 30 d).
-- Borrado FÍSICO en DeleteUserCommand.eraseIn (ADR-0014 D6).
--
-- UNA FILA POR CONCESIÓN, no una fila por usuario: deliberadamente SIN el UNIQUE (usuario_id,
-- version_texto) que propone docs/arquitectura/rgpd-en-modulos.md §6 — colisiona con el ciclo
-- revocar → volver a conceder sobre la MISMA versión de texto, que forzaría reescribir la fila
-- anterior. ADR-0014 D18 no exige ese UNIQUE, solo enumera columnas; aquí gana el ADR sobre la guía.
-- `revocado_en` sí se actualiza sobre la fila ya insertada (nace NULL, se rellena una vez), pero eso
-- nunca sobrescribe `concedido_en` ni ningún otro dato de la concesión original. El estado vigente del
-- alumno es su fila más reciente por `concedido_en`. Detalle completo en identidad/RGPD.md.
-- `ip` va como TEXT, no INET: Hibernate escribe el parámetro como varchar y Postgres no tiene cast
-- implícito varchar→inet (el propio `identidad.trunca_ip` de V202608210001 necesita un `::inet`
-- explícito por el mismo motivo) — un INSERT vía JPA fallaría con "column is of type inet but
-- expression is of type character varying". `identidad.evento_auditoria.ip` sí es INET porque nunca
-- se escribe por JPA (AuditEventEntity no mapea esa columna); aquí sí hace falta escribirla, así que
-- se guarda como texto validado en el dominio, sin la validación nativa de formato que daría INET.
CREATE TABLE identidad.consentimiento (
    id             UUID                     NOT NULL,
    usuario_id     UUID                     NOT NULL,
    club_id        UUID                     NOT NULL,
    version_texto  VARCHAR(20)              NOT NULL,
    concedido_en   TIMESTAMP WITH TIME ZONE NOT NULL,
    revocado_en    TIMESTAMP WITH TIME ZONE NULL,
    ip             TEXT                     NOT NULL,  -- completa a propósito (ADR-0014 D18)
    user_agent     TEXT                     NOT NULL,
    creado_en      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT consentimiento_pk PRIMARY KEY (id)
);

-- Soporta "la fila más reciente de este usuario" (ConsentRepositoryImpl.findLatestByUserId).
CREATE INDEX consentimiento_usuario_idx ON identidad.consentimiento (usuario_id, concedido_en DESC);
