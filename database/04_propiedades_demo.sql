-- Diez propiedades ficticias para la demostración de Nuevo Hogar.
-- Ejecutar sobre la base inmobiliaria ya migrada (schema_version = 2).
-- Reejecutable: no duplica propiedades, ofertas, publicaciones ni referencias de fotos.
-- Las fotos se deben copiar en data/archivos/demo/ con los nombres indicados
-- en data/archivos/demo/LEEME.txt. Sin esos archivos se muestra la ilustración de reemplazo.

START TRANSACTION;

SET @agent_id = (SELECT id_usuario FROM usuarios
                 WHERE email = 'agente.demo@nuevohogar.local' LIMIT 1);
SET @client_id = (SELECT id_usuario FROM usuarios
                  WHERE email = 'cliente.demo@nuevohogar.local' LIMIT 1);
SET @client2_id = (SELECT id_usuario FROM usuarios
                   WHERE email = 'cliente2.demo@nuevohogar.local' LIMIT 1);
SET @admin_id = (SELECT id_usuario FROM usuarios
                 WHERE email = 'admin.demo@nuevohogar.local' LIMIT 1);

-- Si falta alguna cuenta demo, estas inserciones no agregan propiedades.
SELECT CASE
         WHEN @agent_id IS NULL OR @client_id IS NULL
              OR @client2_id IS NULL OR @admin_id IS NULL
         THEN 'Faltan las cuentas demo. Importá primero database/03_datos_demo.sql.'
         ELSE 'Cuentas demo encontradas: se cargarán las propiedades.'
       END AS resultado_previo;

INSERT INTO propiedades
    (id_tipo_propiedad, id_tipo_transaccion, titulo, direccion, localidad,
     precio, moneda, descripcion, superficie_total_m2, superficie_cubierta_m2,
     antiguedad_anos, ambientes, dormitorios, banos, expensas, apto_profesional,
     id_agente_creador, id_admin_verificador, id_estado_verificacion,
     id_estado_comercial, fecha_verificacion, id_propietario, capacidad)
SELECT d.tipo, d.operacion, d.titulo, d.direccion, d.localidad,
       d.precio, d.moneda, d.descripcion, d.total_m2, d.cubierta_m2,
       d.antiguedad, d.ambientes, d.dormitorios, d.banos, d.expensas,
       d.apto_profesional, @agent_id, @admin_id, 3, 1, CURRENT_TIMESTAMP,
       IF(d.propietario = 2, @client2_id, @client_id), d.capacidad
FROM (
    SELECT 1 AS tipo, 1 AS operacion,
           'PH reciclado con terraza en Villa Urquiza' AS titulo,
           'Mendoza 4820' AS direccion, 'Villa Urquiza, CABA' AS localidad,
           169000.00 AS precio, 'USD' AS moneda,
           'PH de tres ambientes reciclado, con terraza propia, parrilla y entrada independiente. A pocas cuadras del subte y de la estación.' AS descripcion,
           112.00 AS total_m2, 78.00 AS cubierta_m2, 18 AS antiguedad,
           3 AS ambientes, 2 AS dormitorios, 1 AS banos, 0.00 AS expensas,
           0 AS apto_profesional, 1 AS propietario, 4 AS capacidad
    UNION ALL SELECT 1, 1,
           'Casa familiar con jardín en Martínez', 'Sáenz Valiente 1840',
           'Martínez, Buenos Aires', 289000.00, 'USD',
           'Casa de cuatro ambientes con jardín, galería semicubierta y cochera. Ambientes luminosos y buena conexión con accesos y comercios.',
           245.00, 168.00, 14, 4, 3, 2, 0.00, 0, 1, 6
    UNION ALL SELECT 1, 2,
           'Casa amueblada con pileta en Tigre', 'Los Ceibos 740', 'Tigre, Buenos Aires',
           1450.00, 'USD',
           'Alquiler temporario de casa amueblada con pileta, galería, parrilla y parque. Ideal para estadías familiares.',
           310.00, 176.00, 11, 5, 3, 2, 0.00, 0, 1, 6
    UNION ALL SELECT 2, 1,
           'Departamento con balcón en Caballito', 'Av. Pedro Goyena 860', 'Caballito, CABA',
           215000.00, 'USD',
           'Departamento de tres ambientes con balcón al frente, cocina independiente y espacio para lavarropas. Edificio con ascensor y seguridad.',
           96.00, 88.00, 7, 3, 2, 2, 98000.00, 0, 2, 3
    UNION ALL SELECT 3, 1,
           'Local a la calle en Belgrano', 'Juramento 2180', 'Belgrano, CABA',
           198000.00, 'USD',
           'Local comercial en corredor de alto tránsito, con vidriera amplia, salón principal y baño. Apto para distintos rubros.',
           78.00, 78.00, 22, 2, 0, 1, 0.00, 1, 2, 2
    UNION ALL SELECT 5, 1,
           'Lote arbolado en barrio privado de Escobar', 'Lote 184, Barrio Los Álamos', 'Escobar, Buenos Aires',
           72000.00, 'USD',
           'Terreno nivelado y arbolado dentro de barrio privado con servicios, espacios verdes y acceso controlado.',
           820.00, 0.00, 0, 0, 0, 0, 185000.00, 0, 1, 1
    UNION ALL SELECT 4, 2,
           'Oficina luminosa cerca de Plaza San Martín', 'Maipú 480, piso 6', 'Microcentro, CABA',
           1350000.00, 'ARS',
           'Oficina en alquiler con recepción, dos despachos, sala de reuniones y kitchenette. Edificio con ascensores y control de acceso.',
           92.00, 92.00, 16, 4, 0, 1, 210000.00, 1, 2, 5
    UNION ALL SELECT 6, 1,
           'Cochera cubierta en Palermo Soho', 'Gurruchaga 1420, subsuelo', 'Palermo, CABA',
           26500.00, 'USD',
           'Cochera fija cubierta en edificio residencial, con portón automático y acceso cómodo desde la calle.',
           14.00, 14.00, 9, 0, 0, 0, 18000.00, 0, 2, 1
    UNION ALL SELECT 1, 2,
           'Chalet frente al mar en alquiler', 'Calle 4 n.º 820', 'Mar del Tuyú, Buenos Aires',
           950000.00, 'ARS',
           'Chalet amueblado a pocas cuadras de la playa, con patio, parrilla y espacio para guardar el auto. Disponible por temporada.',
           190.00, 118.00, 24, 4, 3, 2, 0.00, 0, 1, 6
    UNION ALL SELECT 2, 1,
           'Departamento con amenities en Vicente López', 'Av. del Libertador 1550, piso 8', 'Vicente López, Buenos Aires',
           238000.00, 'USD',
           'Departamento de tres ambientes con balcón y vista abierta. Edificio con pileta, SUM, gimnasio y seguridad permanente.',
           105.00, 94.00, 4, 3, 2, 2, 175000.00, 0, 2, 3
) AS d
WHERE @agent_id IS NOT NULL AND @client_id IS NOT NULL
  AND @client2_id IS NOT NULL AND @admin_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM propiedades p WHERE p.titulo = d.titulo);

INSERT IGNORE INTO propiedad_ofertas (id_propiedad, id_tipo_transaccion, precio, moneda)
SELECT p.id_propiedad, d.operacion, d.precio, d.moneda
FROM (
    SELECT 'PH reciclado con terraza en Villa Urquiza' AS titulo, 1 AS operacion, 169000.00 AS precio, 'USD' AS moneda
    UNION ALL SELECT 'Casa familiar con jardín en Martínez', 1, 289000.00, 'USD'
    UNION ALL SELECT 'Casa amueblada con pileta en Tigre', 2, 1450.00, 'USD'
    UNION ALL SELECT 'Departamento con balcón en Caballito', 1, 215000.00, 'USD'
    UNION ALL SELECT 'Local a la calle en Belgrano', 1, 198000.00, 'USD'
    UNION ALL SELECT 'Lote arbolado en barrio privado de Escobar', 1, 72000.00, 'USD'
    UNION ALL SELECT 'Oficina luminosa cerca de Plaza San Martín', 2, 1350000.00, 'ARS'
    UNION ALL SELECT 'Cochera cubierta en Palermo Soho', 1, 26500.00, 'USD'
    UNION ALL SELECT 'Chalet frente al mar en alquiler', 2, 950000.00, 'ARS'
    UNION ALL SELECT 'Departamento con amenities en Vicente López', 1, 238000.00, 'USD'
) AS d
JOIN propiedades p ON p.titulo = d.titulo;

INSERT INTO publicaciones
    (id_propiedad, id_solicitante, id_revisor, estado, observaciones, fecha_revision)
SELECT p.id_propiedad, @agent_id, @admin_id, 'Publicada',
       'Propiedad ficticia aprobada para la demostración.', CURRENT_TIMESTAMP
FROM propiedades p
WHERE p.titulo IN (
    'PH reciclado con terraza en Villa Urquiza',
    'Casa familiar con jardín en Martínez',
    'Casa amueblada con pileta en Tigre',
    'Departamento con balcón en Caballito',
    'Local a la calle en Belgrano',
    'Lote arbolado en barrio privado de Escobar',
    'Oficina luminosa cerca de Plaza San Martín',
    'Cochera cubierta en Palermo Soho',
    'Chalet frente al mar en alquiler',
    'Departamento con amenities en Vicente López'
)
AND NOT EXISTS (
    SELECT 1 FROM publicaciones s
    WHERE s.id_propiedad = p.id_propiedad AND s.estado = 'Publicada'
);

-- Registra los nombres esperados por el catálogo. Copiá cada foto real en
-- data/archivos/demo/ con el nombre correspondiente antes de abrir la app.
INSERT INTO propiedad_multimedia (id_propiedad, url_archivo, tipo, orden)
SELECT p.id_propiedad, CONCAT('demo/', d.archivo), 'Imagen', 1
FROM (
    SELECT 'PH reciclado con terraza en Villa Urquiza' AS titulo, 'propiedad-01.jpg' AS archivo
    UNION ALL SELECT 'Casa familiar con jardín en Martínez', 'propiedad-02.jpg'
    UNION ALL SELECT 'Casa amueblada con pileta en Tigre', 'propiedad-03.jpg'
    UNION ALL SELECT 'Departamento con balcón en Caballito', 'propiedad-04.jpg'
    UNION ALL SELECT 'Local a la calle en Belgrano', 'propiedad-05.jpg'
    UNION ALL SELECT 'Lote arbolado en barrio privado de Escobar', 'propiedad-06.jpg'
    UNION ALL SELECT 'Oficina luminosa cerca de Plaza San Martín', 'propiedad-07.jpg'
    UNION ALL SELECT 'Cochera cubierta en Palermo Soho', 'propiedad-08.jpg'
    UNION ALL SELECT 'Chalet frente al mar en alquiler', 'propiedad-09.jpg'
    UNION ALL SELECT 'Departamento con amenities en Vicente López', 'propiedad-10.jpg'
) AS d
JOIN propiedades p ON p.titulo = d.titulo
WHERE NOT EXISTS (
    SELECT 1 FROM propiedad_multimedia m
    WHERE m.id_propiedad = p.id_propiedad AND m.url_archivo = CONCAT('demo/', d.archivo)
);

COMMIT;

SELECT p.id_propiedad, p.titulo, p.localidad, p.precio, p.moneda,
       t.nombre AS tipo, x.nombre AS operacion,
       CONCAT('data/archivos/', m.url_archivo) AS foto_esperada
FROM propiedades p
JOIN tipos_propiedad t ON t.id_tipo_propiedad = p.id_tipo_propiedad
JOIN tipos_transaccion x ON x.id_tipo_transaccion = p.id_tipo_transaccion
LEFT JOIN propiedad_multimedia m
       ON m.id_propiedad = p.id_propiedad AND m.tipo = 'Imagen'
WHERE p.titulo IN (
    'PH reciclado con terraza en Villa Urquiza',
    'Casa familiar con jardín en Martínez',
    'Casa amueblada con pileta en Tigre',
    'Departamento con balcón en Caballito',
    'Local a la calle en Belgrano',
    'Lote arbolado en barrio privado de Escobar',
    'Oficina luminosa cerca de Plaza San Martín',
    'Cochera cubierta en Palermo Soho',
    'Chalet frente al mar en alquiler',
    'Departamento con amenities en Vicente López'
)
ORDER BY p.id_propiedad;
