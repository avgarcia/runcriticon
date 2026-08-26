# RGPD — módulo `identidad`

Espejo aplicado de ADR-0014. Si hay conflicto, gana el ADR.

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| `identidad.usuario` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `identidad.invitacion` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `identidad.magic_link` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `identidad.password_historico` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `identidad.consentimiento` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `identidad.evento_auditoria` | 2 — Auditoría local | 12 meses (purga pendiente) | Anonimización (no borrado) |

## Consentimiento de datos de salud (LAL-128, ADR-0014 D16/D18)

Base legal del tratamiento que captura `seguimiento.reporte_sesion` (LAL-30): **consentimiento
explícito, Art. 9.2.a RGPD**. Solo lo concede el **ALUMNO** — es el único interesado de esos datos;
ADMIN/ENTRENADOR activan su cuenta sin ninguna casilla y no les afecta ninguna puerta.

### Tabla `identidad.consentimiento` — una fila por concesión, no por usuario

```sql
CREATE TABLE identidad.consentimiento (
    id             UUID PRIMARY KEY,
    usuario_id     UUID        NOT NULL,
    club_id        UUID        NOT NULL,
    version_texto  VARCHAR(20) NOT NULL,
    concedido_en   TIMESTAMPTZ NOT NULL,
    revocado_en    TIMESTAMPTZ NULL,
    ip             TEXT        NOT NULL,
    user_agent     TEXT        NOT NULL,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Diverge deliberadamente de `docs/arquitectura/rgpd-en-modulos.md` §6**, que propone un
`UNIQUE (usuario_id, version_texto)`: esa restricción colisiona con el ciclo revocar → volver a
conceder sobre la **misma** versión de texto (el caso más común: nada cambia en el texto entre una
revocación y un arrepentimiento), y forzaría reescribir la fila anterior con un `UPDATE`, perdiendo
el registro de la concesión original. `ADR-0014` D18 no exige ese `UNIQUE` — solo enumera las
columnas — así que aquí gana el ADR sobre la guía operativa.

En la práctica: **conceder siempre inserta una fila nueva** (`GrantConsentCommand`/
`ActivateAccountCommand`); **revocar actualiza `revocado_en`** de la fila vigente
(`RevokeConsentCommand`) — nunca sobrescribe `concedido_en` ni ningún otro dato de la concesión
original, así que no hay pérdida de información pese a la actualización. El estado vigente de un
alumno es su fila más reciente por `concedido_en` (`ConsentRepositoryImpl.findLatestByUserId`).

**`ip` es `TEXT`, no `INET`**: Hibernate escribe el parámetro como `varchar` y Postgres no tiene cast
implícito `varchar→inet` (mismo motivo por el que `identidad.trunca_ip` necesita un `::inet`
explícito). `identidad.evento_auditoria.ip` sí es `INET` porque nunca se escribe por JPA — aquí sí
hace falta escribirla, así que se acepta el texto sin la validación nativa de formato de `INET`.

### Flujo

- **Concesión**: al activar la cuenta (`ActivateAccountCommand`, casilla no premarcada en el
  frontend, solo para ALUMNO) o desde `/me/consentimiento` (`GrantConsentCommand` — cubre a quien
  activó antes de que existiera este mecanismo, y a quien vuelve a conceder tras revocar).
  Idempotente si ya está vigente: no crea una fila redundante ni reemite el evento.
- **Revocación**: `/me/consentimiento` DELETE (`RevokeConsentCommand`). Publica
  `ConsentimientoRevocado`; el módulo `seguimiento` deja de aceptar nuevos reportes de sesión de ese
  alumno hasta que vuelva a conceder (puerta fail-closed, LAL-128 PR2).
- **Versión del texto**: `ConsentText.CURRENT_VERSION` en dominio, sincronizada a mano con
  `docs/legal/consentimiento/{version}.md`. Conceder sobre una versión que no coincide con la vigente
  rechaza con `ConsentTextOutdated` (409 `VERSION_CONSENTIMIENTO_OBSOLETA`).

### Auditoría

Cada concesión/revocación deja un asiento en `identidad.evento_auditoria`
(`CONSENTIMIENTO_CONCEDIDO`/`CONSENTIMIENTO_REVOCADO`) — es auditoría local del módulo (categoría 2),
no `AccesoADatosSensibles`: conceder/revocar el propio consentimiento es la misma exención de
"lectura del propio perfil del usuario" que documenta `rgpd-en-modulos.md` §5.

## Eventos publicados relacionados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `ConsentimientoConcedido` | Al conceder (activación o `/me/consentimiento`) | `seguimiento` (proyección local de qué alumnos pueden reportar) |
| `ConsentimientoRevocado` | Al revocar | `seguimiento` (rechaza nuevos reportes hasta que vuelva a conceder) |

## Borrado al ejercer el derecho de supresión

`DeleteUserCommand.eraseIn` borra `identidad.consentimiento` junto con `invitacion`, `magic_link` y
`password_historico`, antes de borrar `usuario` (sin `ON DELETE CASCADE` declarado en ninguna).

## Pendientes jurídicos del módulo

- **Redacción real de los textos de consentimiento** (`docs/legal/consentimiento/`): hoy es un
  borrador funcional marcado explícitamente como tal, pendiente de asesoría legal (ADR-0014,
  pendiente jurídico general de D16).
- **Criterios de "cambio sustancial"** que dispararía la reconfirmación masiva de todos los alumnos
  activos (D18) — sin criterio legal, no se construye ese flujo todavía.
- **RAT** (`docs/legal/rat.md`, ADR-0014 D19): creado con las entradas conocidas, pendiente de
  validación legal completa.
