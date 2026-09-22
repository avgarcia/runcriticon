# Runbook — atención de una solicitud de oposición o limitación (Arts. 21 y 18 RGPD)

> Invocado por ADR-0014 D15 (oposición y limitación: runbook manual). D15 fija que, en la práctica,
> **oponerse = revocar el consentimiento (D18) y/o pedir la supresión (D14)**. Este runbook guía esa
> conversación con el titular y ejecuta lo que existe hoy. La **limitación** (Art. 18) **no tiene
> mecanismo implementado**: ver la sección dedicada.

## Cuándo se ejecuta

- El titular manifiesta que no quiere que se sigan tratando sus datos (oposición, Art. 21), o pide
  que se conserven pero no se usen mientras se resuelve una reclamación (limitación, Art. 18), por
  correo a `privacidad@runcriticon.com` (D11) o a través del admin de su club.

## Quién puede ejecutarlo

- El **responsable del tratamiento** (ADR-0014 D23) conduce la solicitud y la conversación con el
  titular.
- La revocación del consentimiento la ejecuta **el propio titular** desde la aplicación: en la
  `AuthorizationMatrix` solo el rol `ALUMNO` tiene `CONSENT:REVOKE`, y el endpoint actúa sobre el
  usuario autenticado (`DELETE /api/me/consentimiento`). **No hay vía de admin** para revocar en nombre
  de otro.
- La supresión, si el titular la elige, la ejecuta el **ADMIN** del club según
  [`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md).

## Plazo

- **1 mes** desde la solicitud (Art. 12.3, D11), prorrogable a **3 meses** con comunicación al
  interesado dentro del primer mes.
- La revocación surte efecto en el módulo `seguimiento` en cuanto se procesa `ConsentimientoRevocado`
  (NFR de ADR-0014: < 24 h p95 hasta el cese del tratamiento dependiente).

## Prerrequisitos

- [ ] La solicitud está registrada (email del titular, fecha, club, derecho ejercido y motivo si lo
      da) en `<pendiente: ubicación del registro de solicitudes RGPD — fuera del repo, nunca en git
      porque contiene PII>`.
- [ ] Verificación de identidad completada: **email vigente** de la cuenta + confirmación **fuera de
      banda** con el admin del club (mismo criterio que D12 y que el runbook de supresión).
- [ ] Se conoce el `usuarioId`, el rol y el `club_id` de la persona.

## Procedimiento

### 1. Determinar qué pide realmente el titular

Oposición y limitación no son lo mismo, y el RGPD las trata distinto. Aclararlo con el titular y dejar
constancia en el registro de la opción elegida:

| El titular quiere… | Camino | Sección |
|---|---|---|
| Que se dejen de tratar sus **datos de salud** pero seguir usando la cuenta | Revocar el consentimiento (D18) | 2 |
| Que se deje de tratar **todo** y no volver | Supresión (D14) | 3 |
| Que se conserven sus datos pero **no se usen** (p. ej. mientras reclama una inexactitud) | Limitación (Art. 18) | 4 |

La base legal del tratamiento de datos de salud es el **consentimiento explícito** (Art. 9.2.a,
ADR-0014 D16), no el interés legítimo: por eso la oposición se canaliza como revocación — retirar el
consentimiento es la forma de hacer cesar un tratamiento basado en él.

### 2. Revocación del consentimiento (vía el propio titular)

Indicar al titular que la revoque él mismo desde **"Mi cuenta"** (`/mi-cuenta` en la aplicación), que
llama a `DELETE /api/me/consentimiento` (`RevokeConsentCommand`). Solo aplica a titulares con rol
`ALUMNO`: ADMIN y ENTRENADOR no conceden consentimiento de datos de salud (ver
`identidad/RGPD.md`), así que para ellos la oposición se reconduce a la supresión (sección 3).

> ⚠️ **Nunca** revocar a mano con un `UPDATE identidad.consentimiento SET revocado_en = …`. El caso de
> uso publica `ConsentimientoRevocado` al outbox; un `UPDATE` directo no, así que la proyección
> `seguimiento.consentimiento_alumno` seguiría con `vigente = true` y la puerta que bloquea el
> tratamiento quedaría abierta. El dato diría "revocado" y el sistema seguiría tratando.

**Qué deja de hacerse tras la revocación** (verificado en código):

- `SubmitSessionReportCommand` — el alumno ya no puede enviar reportes de sesión
  (`409 CONSENTIMIENTO_NO_VIGENTE`).
- `RescheduleDayCommand` — ya no puede reajustar días (mismo error).

**Qué NO se detiene** (explicarlo al titular; si no le basta, el camino es la supresión o la
limitación):

- `RecordMarkCommand` — las marcas personales no consultan el consentimiento (su KDoc documenta que
  D18 ata el consentimiento a `reporte_sesion`).
- **Los datos ya recogidos no se borran**: reportes, reajustes y marcas anteriores siguen en
  `seguimiento` y siguen visibles para su entrenador (p. ej. `ListCoachAlertsQuery` no consulta el
  consentimiento). Revocar detiene la recogida nueva, no el tratamiento de lo ya recogido.
- El plan de entrenamiento se sigue publicando y personalizando (`planificacion` no consulta el
  consentimiento).

**Verificación** (consulta de solo lectura; acceso a la BD según `<pendiente: acceso-rds.md no existe
todavía, ADR-0006 D13>`):

```sql
-- identidad: la concesión más reciente debe tener revocado_en relleno
SELECT version_texto, concedido_en, revocado_en
FROM identidad.consentimiento
WHERE usuario_id = '<usuarioId>'
ORDER BY concedido_en DESC LIMIT 1;

-- seguimiento: la proyección debe decir vigente = false
SELECT vigente, version_texto, last_processed_event_ts
FROM seguimiento.consentimiento_alumno
WHERE alumno_id = '<usuarioId>';

-- auditoría local del módulo identidad: asiento de la revocación
SELECT tipo, ts FROM identidad.evento_auditoria
WHERE sujeto_id = '<usuarioId>' AND tipo = 'CONSENTIMIENTO_REVOCADO'
ORDER BY ts DESC LIMIT 1;
```

Si `identidad` dice revocado pero la proyección sigue en `vigente = true` pasados unos minutos, el
evento se ha quedado en el outbox: seguir el paso 4 de
[`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md) ("Si la propagación falla") filtrando por
`event_type` de `ConsentimientoRevocado`.

### 3. Supresión

Si el titular quiere que se deje de tratar todo, ejecutar
[`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md) completo. Es irreversible: confirmar con el
titular que entiende la diferencia con la revocación antes de lanzarlo.

### 4. Limitación del tratamiento (Art. 18) — sin mecanismo implementado

ADR-0014 D15 fija un estado `LIMITADO` en `identidad.usuario` y que `seguimiento` rechace operaciones
de un usuario limitado. **Nada de eso existe**:

- El `CHECK` de `identidad.usuario.estado` solo admite `INVITADO`, `ACTIVO` y `DESACTIVADO`
  (`V202606030002__crea_usuario.sql`).
- Ningún módulo consulta un estado de limitación.
- **No figura en ADR-0015** como aplazamiento consciente: es un hueco de implementación frente a un ADR
  aceptado, no una decisión diferida con disparador.

**La desactivación de la cuenta no equivale a limitación** y no debe presentarse como tal:
`POST /api/usuarios/{id}/desactivacion` (`DeactivateUserCommand`) bloquea el login y revoca sesiones,
pero:

- no publica ningún evento, así que el resto de módulos no se entera y los entrenadores siguen
  planificando y viendo los datos del alumno;
- no tiene endpoint de reactivación: deshacerla exigiría un `UPDATE` manual en BD.

**Qué hacer hoy ante una solicitud de limitación**: escalar al responsable del tratamiento, que decide
caso a caso — `<pendiente: decisión del responsable sobre cómo atender una limitación mientras no
exista el estado LIMITADO (implementarlo antes de la beta, o documentar una medida provisional)>`.
Responder al titular dentro del plazo de D11 aunque la respuesta sea una prórroga motivada.

### 5. Cerrar

- Responder al titular confirmando qué se ha hecho (revocación verificada, supresión ejecutada, o
  estado de la limitación) dentro del plazo de D11.
- Registrar fecha de cierre, camino elegido y operador junto a la solicitud.

## Limitaciones conocidas

- **Limitación (Art. 18) sin implementar** — ver sección 4. No hay ticket ni disparador que citar;
  requiere decisión.
- **Revocación solo self-service** — si el titular no puede o no quiere entrar en la aplicación, no hay
  forma soportada de revocar en su nombre. La alternativa soportada es la supresión.
- **La revocación no cubre marcas ni la visibilidad de datos ya recogidos** — ver sección 2.

## Rollback

- **Revocación**: el titular puede volver a conceder desde "Mi cuenta" (`POST /api/me/consentimiento`,
  inserta una fila nueva; no reescribe la revocación, que queda en el histórico).
- **Supresión**: no existe (ver el runbook de supresión).

## Referencias

- ADR-0014 D11 (plazo), D14 (supresión), D15 (oposición y limitación), D16 (base legal), D18
  (consentimiento).
- [`identidad/RGPD.md`](../../backend/src/main/kotlin/com/runcriticon/identidad/RGPD.md) y
  [`seguimiento/RGPD.md`](../../backend/src/main/kotlin/com/runcriticon/seguimiento/RGPD.md) — flujo de
  consentimiento y tablas afectadas.
- [`RevokeConsentCommand`](../../backend/src/main/kotlin/com/runcriticon/identidad/application/usecases/consent/RevokeConsentCommand.kt),
  [`MeController`](../../backend/src/main/kotlin/com/runcriticon/identidad/infrastructure/rest/MeController.kt).
- [`derechos-rgpd-supresion.md`](derechos-rgpd-supresion.md).
