-- RGPD: PII_PRIMARIA — misma tabla identidad.magic_link (token_hash vinculado a la identidad del
-- usuario, ADR-0014, ADR-0003 D13); no cambia de categoría.
-- LAL-12 (ADR-0003 D8): discrimina el propósito del magic link (LOGIN | RESETEO) para aislar login de
-- reseteo (un token de reseteo no vale como login ni al revés). Cambio aditivo y compatible hacia
-- atrás (deploy-then-migrate, ADR-0010 D11): columna nueva NOT NULL con DEFAULT 'LOGIN' que el código
-- anterior (LAL-11) ignora; las filas existentes quedan como LOGIN, su semántica original.
ALTER TABLE identidad.magic_link
    ADD COLUMN proposito VARCHAR(20) NOT NULL DEFAULT 'LOGIN';
