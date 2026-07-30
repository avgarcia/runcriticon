-- Siembra la taxonomía por defecto del club de bootstrap: 5 ejes (nivel, distancia, objetivo, terreno, estado) con
-- sus valores. Un club que arranca con la lista vacía deja al admin ante una pantalla en blanco, que es justo lo que
-- el editor de taxonomía tiene que evitar (riesgo R17 de la validación de wireframes).
--
-- Categoría RGPD: SIN_PII. No crea tablas; puebla `tag_key` y `tag_value`, ambas SIN_PII por la migración que las
-- creó — los ejes y sus valores no son datos de persona física.
--
-- Por qué una migración y no un componente de bootstrap: `IdentidadSeeder` es @Profile("local","staging") y no
-- correría en producción; y un ApplicationRunner tendría que pasar por TaxonomyRepositoryImpl, cuyos métodos llevan
-- @AuthScope(Scope.CLUB) y exigen un Principal en el SecurityContextHolder que un runner no tiene (fallaría cerrado).
-- Ventaja añadida: Flyway aplica esto una sola vez, así que un eje que el admin archive o renombre no reaparece
-- en el siguiente arranque.

-- El club es el literal canónico de `runcriticon.bootstrap.club-id`, replicado a mano igual que hizo la migración que
-- creó la fila del club: no hay `placeholders:` configurados en Flyway y parametrizarlo obligaría a tocar esa config.
-- Sin FK a `identidad.club`: son esquemas distintos y ninguna FK cruza la frontera de un módulo.

-- El orden de presentación no lo decide SQL. Los repositorios no ordenan y es TaxonomyMapper quien ordena en memoria
-- por (creado_en, id), así que `now()` —constante dentro de la transacción— dejaría las cinco filas empatadas. De ahí
-- el desplazamiento por fila: fija el orden del wireframe sin depender del desempate por UUID.
INSERT INTO club_taxonomia.tag_key (id, club_id, nombre, creado_en)
SELECT
    t.id,
    '00000000-0000-0000-0000-000000000001'::uuid,
    t.nombre,
    now() + t.orden * interval '1 millisecond'
FROM (VALUES
    (1, '00000000-0000-0000-0001-000000000001'::uuid, 'nivel'),
    (2, '00000000-0000-0000-0001-000000000002'::uuid, 'distancia'),
    (3, '00000000-0000-0000-0001-000000000003'::uuid, 'objetivo'),
    (4, '00000000-0000-0000-0001-000000000004'::uuid, 'terreno'),
    (5, '00000000-0000-0000-0001-000000000005'::uuid, 'estado')
) AS t(orden, id, nombre)
-- Sin target: el choque probable no es la clave primaria sino el índice único parcial por nombre normalizado
-- (tag_key_club_nombre_uk), y la forma sin target los cubre todos. Hace la siembra reejecutable sin duplicar.
ON CONFLICT DO NOTHING;

-- `metadata` se deja al DEFAULT de la columna ('{"tipo": "Empty"}') en vez de escribir el JSON a mano: el converter
-- degrada a Empty logueando error si la forma no cuadra y el CHECK solo mira el discriminante, así que un JSON mal
-- escrito aquí perdería el dato sin romper nada. Ninguno de estos valores lleva metadata de carrera; el catálogo de
-- carreras del eje `objetivo` se puebla aparte.
INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre, creado_en)
SELECT
    v.id,
    v.tag_key_id,
    '00000000-0000-0000-0000-000000000001'::uuid,
    v.nombre,
    now() + v.orden * interval '1 millisecond'
FROM (VALUES
    -- nivel
    (1,  '00000000-0000-0000-0002-000000000001'::uuid, '00000000-0000-0000-0001-000000000001'::uuid, 'iniciación'),
    (2,  '00000000-0000-0000-0002-000000000002'::uuid, '00000000-0000-0000-0001-000000000001'::uuid, 'medio'),
    (3,  '00000000-0000-0000-0002-000000000003'::uuid, '00000000-0000-0000-0001-000000000001'::uuid, 'medio-alto'),
    (4,  '00000000-0000-0000-0002-000000000004'::uuid, '00000000-0000-0000-0001-000000000001'::uuid, 'alto'),
    -- distancia
    (5,  '00000000-0000-0000-0002-000000000005'::uuid, '00000000-0000-0000-0001-000000000002'::uuid, '1500m'),
    (6,  '00000000-0000-0000-0002-000000000006'::uuid, '00000000-0000-0000-0001-000000000002'::uuid, '5k'),
    (7,  '00000000-0000-0000-0002-000000000007'::uuid, '00000000-0000-0000-0001-000000000002'::uuid, '10k'),
    (8,  '00000000-0000-0000-0002-000000000008'::uuid, '00000000-0000-0000-0001-000000000002'::uuid, 'media maratón'),
    (9,  '00000000-0000-0000-0002-000000000009'::uuid, '00000000-0000-0000-0001-000000000002'::uuid, 'maratón'),
    -- objetivo: solo el valor neutro; las carreras (nombre + fecha + distancia) llegan con su propio catálogo
    (10, '00000000-0000-0000-0002-00000000000a'::uuid, '00000000-0000-0000-0001-000000000003'::uuid, 'sin carrera'),
    -- terreno
    (11, '00000000-0000-0000-0002-00000000000b'::uuid, '00000000-0000-0000-0001-000000000004'::uuid, 'asfalto'),
    (12, '00000000-0000-0000-0002-00000000000c'::uuid, '00000000-0000-0000-0001-000000000004'::uuid, 'trail'),
    (13, '00000000-0000-0000-0002-00000000000d'::uuid, '00000000-0000-0000-0001-000000000004'::uuid, 'pista'),
    -- estado
    (14, '00000000-0000-0000-0002-00000000000e'::uuid, '00000000-0000-0000-0001-000000000005'::uuid, 'activo'),
    (15, '00000000-0000-0000-0002-00000000000f'::uuid, '00000000-0000-0000-0001-000000000005'::uuid, 'lesión'),
    (16, '00000000-0000-0000-0002-000000000010'::uuid, '00000000-0000-0000-0001-000000000005'::uuid, 'post-parto'),
    (17, '00000000-0000-0000-0002-000000000011'::uuid, '00000000-0000-0000-0001-000000000005'::uuid, 'descanso')
) AS v(orden, id, tag_key_id, nombre)
-- Si el eje no llegó a crearse porque su nombre ya estaba ocupado en ese club, sus valores tampoco se insertan: sin
-- esta guarda la FK a `tag_key` haría fallar la migración entera y con ella el arranque de la aplicación.
WHERE EXISTS (SELECT 1 FROM club_taxonomia.tag_key k WHERE k.id = v.tag_key_id)
ON CONFLICT DO NOTHING;
