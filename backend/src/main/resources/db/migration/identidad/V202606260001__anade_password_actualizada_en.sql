-- Añade la marca temporal de fijación de contraseña a identidad.usuario (ADR-0003 D7: caducidad de
-- contraseña a 90 días). Cambio aditivo y compatible hacia atrás (deploy-then-migrate, ADR-0010
-- D11): columna nullable que el código anterior ignora sin romperse.
--
-- Categoría RGPD: la tabla identidad.usuario es PII_PRIMARIA (ADR-0014); esta columna es metadato de
-- credencial (no es PII en sí), pero hereda la categoría y el tratamiento de borrado de su tabla.

ALTER TABLE identidad.usuario
    ADD COLUMN password_actualizada_en TIMESTAMP WITH TIME ZONE;

-- Backfill de cuentas ya activas con contraseña: se toma creado_en como aproximación de la última
-- fijación, para no dejarlas caducadas ni eternamente frescas. Las cuentas solo-magic-link
-- (password_hash NULL) y las invitadas se quedan en NULL (la caducidad D7 no les aplica).
UPDATE identidad.usuario
   SET password_actualizada_en = creado_en
 WHERE estado = 'ACTIVO'
   AND password_hash IS NOT NULL
   AND password_actualizada_en IS NULL;
