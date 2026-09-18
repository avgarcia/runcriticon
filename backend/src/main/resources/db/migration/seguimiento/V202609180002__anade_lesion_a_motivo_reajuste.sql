-- Añade LESION al catálogo de motivos del reajuste de día (LAL-131, wireframe 07 §Flujo B opción 4:
-- "Avisar de lesión"). Aditivo: solo amplía el CHECK, ninguna fila existente lo necesita todavía.
--
-- Categoría RGPD: sin cambio de esquema, la tabla ya es PII_PRIMARIA (V202609020001) — motivo LESION es
-- dato de salud igual que MOLESTIAS.
ALTER TABLE seguimiento.reajuste_dia DROP CONSTRAINT reajuste_dia_motivo_check;

ALTER TABLE seguimiento.reajuste_dia ADD CONSTRAINT reajuste_dia_motivo_check
    CHECK (motivo IN ('CANSANCIO', 'MOLESTIAS', 'IMPREVISTO', 'LESION'));
