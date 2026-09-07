# Decisiones y evidencias pendientes

## Estado

Las preguntas funcionales que modificaban alcance, roles, permisos, grupos, planificación, reportes, alertas, comunicación y dashboard han quedado resueltas en esta fase.

Este documento no reabre esas decisiones. Registra únicamente asuntos que requieren autoridad jurídica, de negocio u operativa, o evidencia de validación antes de cerrar la documentación y comenzar la beta.

## 1. Bloqueantes de beta

### PEN-P0-01 — Base jurídica y roles de tratamiento

- **Pregunta pendiente:** ¿qué base jurídica se aplica a cada tratamiento y cuál es la relación formal entre Runcriticon y el club?
- **Por qué bloquea:** el producto procesa identidad, actividad deportiva, molestias, comentarios y disponibilidad.
- **Decisión funcional ya tomada:** Entrenadores asignados y Administradores acceden a los datos deportivos definidos en el backlog.
- **Evidencia requerida:** informe jurídico, textos aprobados y actualización del RAT.
- **Responsable:** Pendiente de asignar.
- **Fecha límite:** Antes de beta con datos reales.
- **Revisor humano:** Privacidad/DPO.

### PEN-P0-02 — Retención, borrado, anonimización y reutilización de email

- **Pregunta pendiente:** ¿qué datos se eliminan, conservan o anonimizan cuando se borra una cuenta con planes, reportes, comentarios, alertas y autoría histórica?
- **Por qué bloquea:** se ha decidido permitir eliminar una cuenta activa y reutilizar su email, pero no puede ejecutarse sin reglas legales y técnicas por categoría de dato.
- **Decisión funcional ya tomada:** solo el Administrador elimina cuentas no administrativas mediante confirmación reforzada; las solicitudes del Alumno y eliminación de Administradores se gestionan por runbook.
- **Evidencia requerida:** tabla por entidad/categoría, plazo, fundamento, estrategia de anonimización y pruebas de borrado.
- **Responsable:** Pendiente de asignar.
- **Fecha límite:** Antes de implementar la eliminación y, en todo caso, antes de beta.
- **Revisor humano:** Privacidad/DPO y Arquitectura.

### PEN-P0-03 — RAT, DPIA y DPO

- **Pregunta pendiente:** ¿qué artefactos exactos exige la revisión jurídica y quién los mantiene?
- **Por qué bloquea:** la documentación actual declara obligatorio el RAT y mantiene pendiente el análisis de DPIA y DPO.
- **Evidencia requerida:** RAT versionado, decisión de DPIA, análisis de DPO y propietario de mantenimiento.
- **Responsable:** Pendiente de asignar.
- **Fecha límite:** Antes de beta.
- **Revisor humano:** Privacidad/DPO.

### PEN-P0-04 — Runbooks operativos

- **Pregunta pendiente:** ¿quién ejecuta y escala eliminación, derechos de acceso, brechas, recuperación y administración excepcional de cuentas?
- **Por qué bloquea:** varias operaciones deliberadamente no tienen interfaz autoservicio.
- **Evidencia requerida:** runbook versionado y ensayo registrado para cada escenario.
- **Responsable:** Pendiente de asignar.
- **Fecha límite:** Antes de beta.
- **Revisor humano:** Operaciones, Seguridad y Privacidad.

## 2. Decisiones de negocio pendientes

### PEN-P0-05 — Métricas de éxito del piloto

- **Pregunta pendiente:** ¿qué resultados harán que el piloto continúe, itere o se detenga?
- **Mínimo que debe definirse:** activación, tiempo de publicación semanal, porcentaje de sesiones reportadas, uso del ritmo relativo, atención de alertas y retención del club piloto.
- **Evidencia requerida:** fórmula, fuente, ventana, objetivo, fecha de revisión y responsable por métrica.
- **Responsable:** Pendiente de asignar.
- **Fecha límite:** Antes de iniciar beta.
- **Revisor humano:** Sponsor y Responsable de producto.

### PEN-P1-01 — Modelo comercial posterior al MVP

- **Pregunta pendiente:** ¿quién paga, por qué unidad y con qué disposición?
- **Clasificación:** no bloquea la construcción del gestor de entrenamientos; sí bloquea declarar viabilidad SaaS demostrada.
- **Restricción:** no introducir pagos o membresías en el MVP para intentar resolver esta pregunta.
- **Evidencia requerida:** entrevistas de precio, prueba de oferta o compromiso del club piloto.
- **Responsable:** Pendiente de asignar.
- **Fecha límite:** Antes de decidir generalización multi-club.
- **Revisor humano:** Negocio y Producto.

## 3. Validaciones necesarias, no nuevas decisiones de alcance

### VAL-P1-01 — Alta masiva por pegado de emails

- **Hipótesis:** es más sencilla que CSV para el club piloto.
- **Prueba mínima:** un Administrador pega y revisa al menos 50 emails, incluyendo duplicados, inválidos e invitaciones pendientes.
- **Criterio propuesto:** completar el envío sin soporte ni pérdida de entradas válidas.
- **Confianza actual:** Media.
- **Responsable:** Pendiente de asignar.

### VAL-P1-02 — Constructor estructurado sin texto libre

- **Hipótesis:** los cinco tipos de bloque, distancia/duración, repetición simple y cuatro intensidades pueden representar las sesiones de running necesarias para el piloto.
- **Prueba mínima:** reconstruir una muestra real de semanas del club, incluyendo series, rodajes, recuperaciones y calentamientos.
- **Criterio propuesto:** ninguna sesión de running del corpus necesita Técnica, Fuerza, texto libre o repetición anidada para resultar comprensible.
- **Confianza actual:** Media. Este es el mayor riesgo funcional decidido.
- **Responsable:** Pendiente de asignar.

### VAL-P1-03 — Ritmo relativo sin aviso por marca ausente

- **Hipótesis:** mostrar la fórmula sin resolver es suficiente y el Alumno gestionará su marca sin intervención del Entrenador.
- **Prueba mínima:** observar activación y primera semana de alumnos con y sin marcas.
- **Criterio propuesto:** la ausencia de marca no provoca ejecución errónea ni consultas de soporte recurrentes.
- **Confianza actual:** Media-baja.
- **Responsable:** Pendiente de asignar.

### VAL-P1-04 — Ruido de alertas

- **Hipótesis:** los umbrales globales iniciales producen un volumen gestionable y detectan excepciones útiles.
- **Prueba mínima:** ejecutar las reglas sobre datos piloto o simulados representativos y revisar el resultado con Entrenadores.
- **Criterio propuesto:** los Entrenadores consideran accionable la mayoría de alertas y pueden gestionarlas sin volver al repaso manual de todos los alumnos.
- **Confianza actual:** Media.
- **Responsable:** Pendiente de asignar.

### VAL-P1-05 — Coste y utilidad del dashboard completo

- **Hipótesis:** el histórico y desglose por Alumno aportan valor suficiente para justificar su inclusión en el MVP.
- **Prueba mínima:** prototipo con últimas 12 semanas y tareas reales de revisión del club.
- **Criterio propuesto:** Administradores y Entrenadores encuentran excepciones y tendencias sin exportar ni reconstruir datos en Excel.
- **Confianza actual:** Media.
- **Responsable:** Pendiente de asignar.

## 4. Propiedad y fechas

Antes de aprobar el traslado a `docs`, cada P0 y P1 de [`backlog-mejoras.md`](backlog-mejoras.md) debe tener:

- Responsable que haya aceptado la tarea.
- Estado verificable.
- Dependencias confirmadas.
- Fecha o hito objetivo cuando corresponda.
- Revisor humano designado.

La ausencia actual de nombres o fechas no se ha rellenado con supuestos.

## Revisión de preguntas bloqueantes

- **Estado:** Bloqueado únicamente para beta y cierre jurídico; listo para revisión humana en producto.
- **Evidencia:** PEN-P0-01 a PEN-P0-05.
- **Hallazgos:** no quedan preguntas funcionales sin clasificar que deban seguir resolviéndose mediante interrogatorio; las restantes requieren responsables especializados o validación empírica.
- **Acción requerida:** asignar responsables, producir evidencias y registrar las decisiones en las fuentes normativas.
- **Revisor humano:** Responsable de producto, Privacidad/DPO y Operaciones según el asunto.
