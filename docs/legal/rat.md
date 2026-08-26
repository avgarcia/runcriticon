# Registro de Actividades de Tratamiento (RAT)

> **Estado: BORRADOR PENDIENTE DE VALIDACIÓN LEGAL.** Este documento cubre el mínimo exigido por
> ADR-0014 D19 (Art. 30 RGPD) a partir de lo que el código trata hoy. No sustituye la validación de
> asesoría legal — pendiente jurídico explícito de ADR-0014. Runcriticon trata datos de salud, así
> que la excepción de "menos de 250 empleados" del Art. 30.5 **no aplica**: el RAT es obligatorio.

Responsable del tratamiento: Runcriticon (constitución formal pendiente, ver ADR-0014 D23 —
pendiente jurídico).

## Tratamiento 1 — Gestión de cuentas de usuario

- **Finalidad**: dar de alta, autenticar y gestionar las cuentas de admin/entrenador/alumno del club.
- **Base legal**: ejecución de la relación contractual con el club (Art. 6.1.b).
- **Categorías de interesados**: administradores, entrenadores y alumnos del club piloto.
- **Categorías de datos**: nombre, email, contraseña (hash), rol, estado de la cuenta.
- **Tablas**: `identidad.usuario`, `identidad.invitacion`, `identidad.magic_link`,
  `identidad.password_historico`.
- **Destinatarios**: ninguno fuera de Runcriticon. Encargados de tratamiento: AWS (infraestructura),
  Postmark (envío de emails).
- **Transferencias internacionales**: ninguna prevista (AWS `eu-west-1`, ADR-0006).
- **Plazos de supresión**: borrado físico al ejercer el derecho de supresión (ADR-0014 D6/D7).
- **Medidas técnicas y organizativas**: contraseñas con Argon2id, tokens de un solo uso hasheados,
  cookies de sesión `httpOnly`/`Secure`, autorización RBAC + nivel de objeto (ADR-0009).

## Tratamiento 2 — Consentimiento de datos de salud (LAL-128)

- **Finalidad**: sostener la base legal del tratamiento 3 (datos de salud) con consentimiento
  explícito demostrable.
- **Base legal**: obligación legal derivada del Art. 9.2.a (el propio consentimiento es la base del
  tratamiento 3; este registro es su evidencia).
- **Categorías de interesados**: alumnos.
- **Categorías de datos**: hecho de haber consentido/revocado, versión del texto, fecha, IP completa,
  user-agent.
- **Tabla**: `identidad.consentimiento`.
- **Destinatarios**: ninguno.
- **Transferencias internacionales**: ninguna.
- **Plazos de supresión**: borrado físico junto con la cuenta del alumno (ADR-0014 D6).
- **Medidas técnicas y organizativas**: casilla no premarcada, acción afirmativa explícita, revocación
  siempre disponible desde "Mi cuenta".

## Tratamiento 3 — Datos de salud del seguimiento deportivo (LAL-30)

- **Finalidad**: que el alumno reporte cómo ha ido cada sesión de entrenamiento (estado, valoración,
  motivo si no la hizo, notas) para que su entrenador ajuste su plan.
- **Base legal**: consentimiento explícito, Art. 9.2.a RGPD (ver Tratamiento 2 y ADR-0014 D16).
- **Categorías de interesados**: alumnos.
- **Categorías de datos especiales**: valoración de sensaciones (1-5), marca de dolor/molestia
  (booleana, derivada del motivo). **No** se captura todavía descripción libre del dolor (columna
  creada, sin rellenar — pendiente jurídico de `seguimiento/RGPD.md`).
- **Otras categorías de datos**: estado (hecho/parcial/no hecho), motivo si no se hizo, notas libres
  para el entrenador.
- **Tabla**: `seguimiento.reporte_sesion`.
- **Destinatarios**: el entrenador del alumno, dentro del club (vista de seguimiento, LAL-34,
  pendiente de construir).
- **Transferencias internacionales**: ninguna.
- **Plazos de supresión**: borrado físico junto con la cuenta del alumno (ADR-0014 D6, categoría 1).
- **Medidas técnicas y organizativas**: puerta de consentimiento vigente antes de aceptar un reporte
  nuevo (módulo `seguimiento`, LAL-128 PR2); sin `AccesoADatosSensibles` para lectura/escritura del
  propio alumno (exención de `rgpd-en-modulos.md` §5).

## Pendiente

- Validación completa por asesoría legal (redacción, base legal, plazos).
- DPIA simplificado (ADR-0014 D20), fuera de este documento.
- Categorización jurídica de la relación con cada club (ADR-0014 D23).
