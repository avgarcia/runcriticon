# Plan de formación — Seguridad de aplicaciones Web/API

Objetivo: construir y revisar Runcriticon **sin agujeros de seguridad**, con especial cuidado porque la aplicación maneja **datos de salud sensibles** (RGPD).

> Recursos transversales: el material de **OWASP** (Top 10, ASVS, *cheat sheets*) y la **Web Security Academy de PortSwigger** (laboratorios prácticos gratuitos). La documentación de **Spring Security** para la parte de implementación.

---

## Nivel 0 — Mentalidad y fundamentos

**Objetivo:** interiorizar cómo piensa la seguridad antes de ver ataques concretos.

- **Modelado de amenazas**: quién podría atacar, qué quiere, por dónde.
- Principios: **mínimo privilegio**, **defensa en profundidad**, **nunca confiar en el cliente**.
- La diferencia entre **autenticación** (quién eres) y **autorización** (qué puedes hacer/ver).

**Conexión Runcriticon:** la "regla de oro" del ADR-0009 — todo se comprueba en el servidor, la UI nunca es la barrera.

---

## Nivel 1 — OWASP Top 10 (aplicaciones web)

**Objetivo:** conocer las diez familias de vulnerabilidades web más habituales.

- Control de acceso roto, fallos criptográficos, inyección (SQL, etc.), diseño inseguro, mala configuración, componentes vulnerables, fallos de identificación/autenticación, fallos de integridad, fallos de registro/monitorización, SSRF.
- Para cada una: cómo se explota y cómo se previene.

**Conexión Runcriticon:** la inyección SQL se previene con consultas parametrizadas (JPA ayuda, pero el SQL nativo del ADR-0004 hay que cuidarlo).

---

## Nivel 2 — OWASP API Security Top 10

**Objetivo:** las vulnerabilidades específicas de las APIs, que es lo que expone el backend.

- **BOLA / IDOR** (autorización rota a nivel de objeto) — la nº 1.
- Autenticación rota, autorización a nivel de propiedad, consumo de recursos sin límite, autorización a nivel de función, etc.

**Conexión Runcriticon:** **el ADR-0009 existe precisamente para cerrar BOLA/IDOR**. Este nivel es la teoría detrás de ese ADR — leerlos juntos.

---

## Nivel 3 — Autenticación y sesiones

**Objetivo:** entender el flujo de entrada y sus riesgos.

- **Cookies de sesión**: atributos `httpOnly`, `SameSite`, `Secure`. Sesión vs JWT.
- **Hashing de contraseñas**: por qué bcrypt/Argon2 y no algo más rápido; *salt*.
- **Tokens** de invitación / reseteo / magic link: un solo uso, caducidad corta, guardados *hasheados*.
- **CSRF** y **XSS**: qué son y cómo se mitigan.
- **Rate limiting** y **throttling** contra fuerza bruta.

**Conexión Runcriticon:** es la teoría del ADR-0003 — cookie de sesión deslizante, Argon2id, tokens hasheados, throttling progresivo.

---

## Nivel 4 — Autorización

**Objetivo:** garantizar que cada usuario solo hace y ve lo suyo.

- **RBAC** (control por rol) y sus límites.
- **Autorización a nivel de objeto** (relación/propiedad) — lo que RBAC no cubre.
- Aislamiento **multi-inquilino** (por `club_id`).

**Conexión Runcriticon:** las tres capas del ADR-0009 (rol, objeto, club).

---

## Nivel 5 — Transporte, datos y privacidad

**Objetivo:** proteger los datos en tránsito y en reposo, y cumplir RGPD.

- **TLS/HTTPS** en todas partes; redirección y *headers* de seguridad (HSTS, CSP…).
- **Cifrado en reposo** de la base de datos y los backups.
- **RGPD** aplicado a datos de salud: minimización, consentimiento, derecho de acceso y borrado, registro de tratamientos.

**Conexión Runcriticon:** la sensibilidad de los datos de salud aparece como driver en ADR-0003, 0004, 0006 y 0009.

---

## Nivel 6 — Cadena de suministro y secretos

**Objetivo:** que no entren agujeros por las dependencias ni por una mala gestión de credenciales.

- **Dependencias vulnerables**: análisis de composición (SCA), mantener librerías al día.
- **Gestión de secretos**: nada de credenciales en el código ni en el repositorio; uso de un gestor de secretos.
- Seguridad de la imagen de contenedor.

**Conexión Runcriticon:** conecta con el Nivel 2 del plan de AWS (Secrets Manager).

---

## Nivel 7 — Seguridad en el ciclo de vida

**Objetivo:** que la seguridad sea un proceso continuo, no una revisión final.

- **Revisión de seguridad** del código (incluida en varios ADR antes del primer usuario real).
- Herramientas **SAST/DAST** en el *pipeline* de CI.
- Registro y monitorización para **detectar** incidentes.

**Conexión Runcriticon:** ADR-0003 pide una revisión de seguridad antes del primer usuario real.

---

## Práctica recomendada

Resolver los laboratorios de la **Web Security Academy de PortSwigger** sobre control de acceso, autenticación y vulnerabilidades de API — atacar para aprender a defender.

## Recursos de partida

- **OWASP**: Top 10, API Security Top 10, ASVS (estándar de verificación) y *cheat sheets*.
- **PortSwigger Web Security Academy** — laboratorios prácticos gratuitos.
- Documentación de **Spring Security**.
- Guías oficiales sobre **RGPD** aplicado a aplicaciones que tratan datos de salud.
