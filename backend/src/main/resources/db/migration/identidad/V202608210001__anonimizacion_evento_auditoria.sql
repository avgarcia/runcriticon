-- Anonimización de identidad.evento_auditoria al ejercer el derecho de supresión. Categoría RGPD:
-- AUDITORIA_IDENTIDAD (categoría 2). Los asientos se anonimizan, no se borran: es el rastro de
-- auditoría que debe sobrevivir a la persona que menciona (patrón de borrado mixto).
--
-- Función auxiliar: trunca una IP de texto a /24 (IPv4) o /48 (IPv6). Devuelve NULL ante cualquier
-- entrada que no parsee como inet (p. ej. el literal "unknown" que usa el resolver de IP cuando no
-- hay remoteAddr) en lugar de abortar la transacción que la invoca.
--
-- Cast a `cidr`, no a `inet`, antes de `set_masklen`: sobre `inet`, `set_masklen` solo cambia la máscara
-- reportada y deja los bits de host intactos (203.0.113.55/24, no anonimiza nada); `cidr` los pone a
-- cero al aplicar la máscara (203.0.113.0/24), que es lo que hace falta aquí.
CREATE OR REPLACE FUNCTION identidad.trunca_ip(p_ip TEXT) RETURNS TEXT AS $$
BEGIN
    RETURN CASE
        WHEN family(p_ip::inet) = 4 THEN set_masklen(p_ip::cidr, 24)::text
        WHEN family(p_ip::inet) = 6 THEN set_masklen(p_ip::cidr, 48)::text
        ELSE NULL
    END;
EXCEPTION
    WHEN others THEN RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Soporta el UPDATE de anonimización (WHERE actor_id = ? OR sujeto_id = ?); ya existía el de sujeto_id.
CREATE INDEX evento_auditoria_actor_id_idx ON identidad.evento_auditoria (actor_id);

-- Soporta el segundo UPDATE, que alcanza los asientos *_RATE_LIMITED: no tienen actor_id ni sujeto_id
-- (flujo anónimo), así que solo son localizables por el email_hash de metadata.
CREATE INDEX evento_auditoria_metadata_email_hash_idx
    ON identidad.evento_auditoria ((metadata ->> 'email_hash'));
